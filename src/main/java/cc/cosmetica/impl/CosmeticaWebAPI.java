/*
 * Copyright 2022, 2023 EyezahMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.cosmetica.impl;

import cc.cosmetica.api.*;
import cc.cosmetica.api.cosmetic.Cosmetic;
import cc.cosmetica.api.cosmetic.CosmeticType;
import cc.cosmetica.api.cosmetic.LoreType;
import cc.cosmetica.api.cosmetic.Model;
import cc.cosmetica.api.cosmetic.OwnedCosmetic;
import cc.cosmetica.api.cosmetic.ShoulderBuddies;
import cc.cosmetica.api.cosmetic.UploadState;
import cc.cosmetica.api.settings.CapeDisplay;
import cc.cosmetica.api.settings.CapeServer;
import cc.cosmetica.api.settings.IconSettings;
import cc.cosmetica.api.settings.UserSettings;
import cc.cosmetica.impl.cosmetic.AbstractCosmetic;
import cc.cosmetica.util.HostProvider;
import cc.cosmetica.util.Response;
import cc.cosmetica.util.SafeURL;
import cc.cosmetica.util.Yootil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CosmeticaWebAPI implements CosmeticaAPI {
	private CosmeticaWebAPI(@Nullable String masterToken, @Nullable String limited) {
		this.masterToken = masterToken;
		this.limitedToken = limited;
		this.loginInfo = Optional.empty();
		this.apiHostProvider = apiHostProviderTemplate.clone();
	}

	private CosmeticaWebAPI(UUID uuid, String limitedToken, @Nullable String client) throws FatalServerErrorException, IOException {
		this.apiHostProvider = apiHostProviderTemplate.clone();
		this.loginInfo = Optional.of(this.exchangeTokens(uuid, limitedToken, client));
	}

	private final Optional<LoginInfo> loginInfo;
	private final HostProvider apiHostProvider;
	private String masterToken;
	private String limitedToken;
	private int timeout = 20 * 1000;
	private Consumer<String> urlLogger = s -> {};

	private boolean forceHttps() {
		return this.apiHostProvider.isForceHttps();
	}

	private LoginInfo exchangeTokens(UUID uuid, String authToken, @Nullable String client) throws IllegalStateException, FatalServerErrorException, IOException {
		SafeURL url = SafeURL.of(this.apiHostProvider.getSecureUrl() + "/client/verifyforauthtokens?uuid=" + uuid + "&client=" + Yootil.urlEncode(client), authToken);

		try (Response response = Response.get(url, this.timeout)) {
			JsonObject object = response.getAsJson();

			if (object.has("error")) {
				throw new CosmeticaAPIException("Error exchanging tokens! " + object.get("error").getAsString());
			}

			this.masterToken = object.get("master_token").getAsString();
			this.limitedToken = object.get("limited_token").getAsString();
			return new LoginInfo(object.get("is_new_player").getAsBoolean(), object.has("has_special_cape") ? object.get("has_special_cape").getAsBoolean() : false);
		}
	}

	@Override
	public Optional<LoginInfo> getLoginInfo() {
		return this.loginInfo;
	}

	@Override
	public ServerResponse<VersionInfo> checkVersion(String minecraftVersion, String cosmeticaVersion) {
		SafeURL versionCheck = createTokenless("/v2/get/versioncheck?modversion="
				+ Yootil.urlEncode(cosmeticaVersion)
				+ "&mcversion=" + Yootil.urlEncode(minecraftVersion), OptionalLong.empty());

		this.urlLogger.accept(versionCheck.safeUrl());

		try (Response response = Response.get(versionCheck, this.timeout)) {
			JsonObject s = response.getAsJson();
			return new ServerResponse<>(new VersionInfo(
					s.get("needsUpdate").getAsBoolean(),
					s.get("isVital").getAsBoolean(),
					s.get("minecraftMessage").getAsString(),
					s.get("plainMessage").getAsString(),
					s.get("megaInvasiveTutorial").getAsBoolean()
			), versionCheck);
		} catch (IOException ie) {
			return new ServerResponse<>(ie, versionCheck);
		} catch (RuntimeException e) {
			return new ServerResponse<>(e, versionCheck);
		}
	}

	@Override
	public ServerResponse<UserInfo> getUserInfo(@Nullable UUID uuid, @Nullable String username, boolean noThirdParty, boolean excludeModels, boolean forceShow) throws IllegalArgumentException {
		if (uuid == null && username == null) throw new IllegalArgumentException("Both uuid and username are null!");

		SafeURL target = createLimited("/v2/get/info?username=" + Yootil.urlEncode(username) + "&uuid=" + Yootil.urlEncode(uuid) + Yootil.urlFlag("nothirdparty", noThirdParty) + Yootil.urlFlag("excludemodels", excludeModels) + Yootil.urlFlag("forceshow", forceShow));
		this.urlLogger.accept(target.safeUrl());

		try (Response response = Response.get(target, this.timeout)) {
			JsonObject jsonObject = response.getAsJson();
			checkErrors(target, jsonObject);

			JsonArray hats = jsonObject.has("hats") ? jsonObject.get("hats").getAsJsonArray() : null;
			JsonObject shoulderBuddies = jsonObject.has("shoulderBuddies") ? jsonObject.get("shoulderBuddies").getAsJsonObject() : null;
			JsonObject backBling = jsonObject.has("backBling") ? jsonObject.get("backBling").getAsJsonObject() : null;
			JsonObject cloak = jsonObject.has("cape") ? jsonObject.get("cape").getAsJsonObject() : null;

			Optional<ShoulderBuddies> sbObj = Optional.empty();

			if (shoulderBuddies != null) {
				sbObj = Optional.of(new ShoulderBuddiesImpl(
						AbstractCosmetic.parse(shoulderBuddies.has("left") ? shoulderBuddies.get("left").getAsJsonObject() : null),
						AbstractCosmetic.parse(shoulderBuddies.has("right") ? shoulderBuddies.get("right").getAsJsonObject() : null)
				));
			}

			JsonObject icon = jsonObject.get("icon").getAsJsonObject();

			return new ServerResponse<>(new UserInfoImpl(
					Yootil.readNullableJsonString(jsonObject.get("skin")),
					jsonObject.get("slim").getAsBoolean(),
					jsonObject.get("lore").getAsString(),
					jsonObject.get("platform").getAsString(),
					jsonObject.get("role").getAsString(),
					jsonObject.get("upsideDown").getAsBoolean(),
					jsonObject.get("prefix").getAsString(),
					jsonObject.get("suffix").getAsString(),
					Yootil.readNullableJsonString(icon.get("client")),
					icon.get("online").getAsBoolean(),
					hats == null ? new ArrayList<>() : Yootil.flatMapObjects(hats, AbstractCosmetic::parse),
					sbObj,
					AbstractCosmetic.parse(backBling),
					AbstractCosmetic.parse(cloak),
					icon.get("icon").getAsString()
			), target);
		} catch (IOException ie) {
			return new ServerResponse<>(ie, target);
		} catch (RuntimeException e) {
			return new ServerResponse<>(e, target);
		}
	}

	@Override
	public ServerResponse<UserSettings> getUserSettings() {
		SafeURL target = createLimited("/v2/get/settings");
		this.urlLogger.accept(target.safeUrl());

		try (Response response = Response.get(target, this.timeout)) {
			JsonObject data = response.getAsJson();
			checkErrors(target, data);

			JsonObject capeSettings = data.get("capeSettings").getAsJsonObject();
			Map<String, CapeServer> oCapeServerSettings = new HashMap<>();

			for (Map.Entry<String, JsonElement> entry : capeSettings.entrySet()) {
				String key = entry.getKey();
				JsonObject setting = capeSettings.get(key).getAsJsonObject();

				oCapeServerSettings.put(key, new CapeServer(
						setting.get("name").getAsString(),
						setting.get("warning").getAsString(),
						setting.get("checkOrder").getAsInt(),
						CapeDisplay.byId(setting.get("setting").getAsInt())
				));
			}

			return new ServerResponse<>(new UserSettingsImpl(
					Yootil.toUUID(data.get("uuid").getAsString()),
					// cosmetics
					data.get("doHats").getAsBoolean(),
					data.get("doShoulderBuddies").getAsBoolean(),
					data.get("doBackBlings").getAsBoolean(),
					data.get("doLore").getAsBoolean(),
					data.get("iconSettings").getAsInt(),
					// other stuff
					data.get("joined").getAsLong(),
					data.get("role").getAsString(),
					data.get("countryCode").getAsString(),
					data.get("perRegionEffects").getAsBoolean(),
					data.get("perRegionEffectsSet").getAsBoolean(),
					data.get("panorama").getAsInt(),
					data.get("onlineActivity").getAsBoolean(),
					oCapeServerSettings
			), target);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, target);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, target);
		}
	}

	/**
	 * Generics hack.
	 * @param <T> the class to force it to reference through generics so the darn thing compiles.
	 */
	private static class GeneralCosmeticType<T extends Cosmetic> {
		private static <T extends Cosmetic> GeneralCosmeticType<T> from(CosmeticType<T> type) {
			return new GeneralCosmeticType<>();
		}

		private static GeneralCosmeticType<Cosmetic> any() {
			return new GeneralCosmeticType<>();
		}
	}

	private <T extends Cosmetic> ServerResponse<CosmeticsPage<T>> getCosmeticsPage(SafeURL url, GeneralCosmeticType<T> cosmeticType) {
		this.urlLogger.accept(url.safeUrl());

		try (Response response = Response.get(url, this.timeout)) {
			JsonObject json = response.getAsJson();
			checkErrors(url, json);

			boolean nextPage = json.get("nextPage").getAsBoolean();
			List<T> cosmetics = new ArrayList<>();

			for (JsonElement element : json.getAsJsonArray("list")) {
				cosmetics.add((T) AbstractCosmetic.parse(element.getAsJsonObject()).get());
			}

			return new ServerResponse<>(new CosmeticsPage<>(cosmetics, nextPage), url);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, url);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, url);
		}
	}

	@Override
	public <T extends Cosmetic> ServerResponse<CosmeticsPage<T>> getRecentCosmetics(CosmeticType<T> type, int page, int pageSize, @NotNull String query) {
		SafeURL url = createTokenless("/get/recentcosmetics?type=" + type.getUrlString() + "&page=" + page + "&pagesize=" + pageSize + "&query=" + Yootil.base64(query), OptionalLong.empty());
		return getCosmeticsPage(url, GeneralCosmeticType.from(type));
	}

	@Override
	public ServerResponse<CosmeticsPage<Cosmetic>> getPopularCosmetics(int page, int pageSize) {
		SafeURL url = createTokenless("/get/popularcosmetics?page=" + page + "&pagesize=" + pageSize, OptionalLong.empty());
		return getCosmeticsPage(url, GeneralCosmeticType.any());
	}

	@Override
	public ServerResponse<CosmeticsPage<Cosmetic>> getOfficialCosmetics(int page, int pageSize) {
		SafeURL url = createTokenless("/get/systemcosmetics?page=" + page + "&pagesize=" + pageSize, OptionalLong.empty());
		return getCosmeticsPage(url, GeneralCosmeticType.any());
	}

	@Override
	public ServerResponse<CosmeticsPage<Cosmetic>> getPendingCosmetics() {
		SafeURL url = createLimited("/get/unverifiedcosmetics");

		try (Response response = Response.get(url)) {
			List<Cosmetic> cosmetics = new ArrayList<>();

			for (JsonElement element : response.getAsJsonArray()) {
				AbstractCosmetic.parse(element.getAsJsonObject()).ifPresent(cosmetics::add);
			}

			return new ServerResponse<>(new CosmeticsPage<>(cosmetics, false), url);
		} catch (IOException ie) {
			return new ServerResponse<>(ie, url);
		} catch (RuntimeException e) {
			return new ServerResponse<>(e, url);
		}
	}

	@Override
	public ServerResponse<List<OwnedCosmetic>> getCosmeticsOwnedBy(@Nullable UUID uuid, @Nullable String username) {
		if (uuid == null && username == null) throw new IllegalArgumentException("Both uuid and username are null!");

		SafeURL url = createMinimalLimited("/get/userownedcosmetics?user=" + Yootil.firstNonNull(uuid, username));

		this.urlLogger.accept(url.safeUrl());

		try (Response response = Response.get(url, this.timeout)) {
			JsonElement json = response.getAsJsonElement();

			// if an object can only be an error
			if (json.isJsonObject()) {
				throw new CosmeticaAPIException(url, json.getAsJsonObject().get("error").getAsString());
			}

			// else, it is an array.
			List<OwnedCosmetic> cosmetics = new ArrayList<>();

			for (JsonElement element : json.getAsJsonArray()) {
				cosmetics.add(OwnedCosmeticImpl.parse(element.getAsJsonObject()));
			}

			return new ServerResponse<>(cosmetics, url);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, url);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, url);
		}
	}

	@Override
	public ServerResponse<List<String>> getLoreList(LoreType type) throws IllegalArgumentException {
		if (type == LoreType.DISCORD || type == LoreType.TWITCH || type == LoreType.NONE) throw new IllegalArgumentException("Invalid lore type for getLoreList: " + type);

		SafeURL url = createLimited("/get/lorelists?type=" + type.toString().toLowerCase(Locale.ROOT));
		this.urlLogger.accept(url.safeUrl());

		try (Response response = Response.get(url, this.timeout)) {
			return new ServerResponse<>(Yootil.toStringList(getAsArray(url, response.getAsJsonElement())), url);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, url);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, url);
		}
	}

	@Override
	public <T extends Cosmetic> ServerResponse<T> getCosmetic(CosmeticType<T> type, String id) {
		SafeURL url = createTokenless("/get/cosmetic?type=" + type.getUrlString() + "&id=" + id, OptionalLong.empty());
		this.urlLogger.accept(url.safeUrl());

		try (Response response = Response.get(url, this.timeout)) {
			JsonObject json = response.getAsJson();
			checkErrors(url, json);

			return new ServerResponse<>((T) AbstractCosmetic.parse(json).get(), url);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, url);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, url);
		}
	}

	@Override
	public ServerResponse<List<Panorama>> getPanoramas() {
		SafeURL url = createLimited("/get/panoramas");
		this.urlLogger.accept(url.safeUrl());

		try (Response response = Response.get(url, this.timeout)) {
			JsonArray json = getAsArray(url, response.getAsJsonElement());
			List<Panorama> result = new ArrayList<>();

			for (JsonElement element : json) {
				JsonObject pano = element.getAsJsonObject();
				result.add(new Panorama(pano.get("id").getAsInt(), pano.get("name").getAsString(), pano.get("free").getAsBoolean()));
			}

			return new ServerResponse<>(result, url);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, url);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, url);
		}
	}

	@Override
	public ServerResponse<CosmeticsUpdates> everyThirtySecondsInAfricaHalfAMinutePasses(InetSocketAddress serverAddress, long timestamp) throws IllegalArgumentException {
		SafeURL awimbawe = create("/get/everythirtysecondsinafricahalfaminutepasses?ip=" + Yootil.base64Ip(serverAddress), OptionalLong.of(timestamp));

		this.urlLogger.accept(awimbawe.safeUrl());

		try (Response theLionSleepsTonight = Response.get(awimbawe, this.timeout)) {
			JsonObject theMightyJungle = theLionSleepsTonight.getAsJson();
			checkErrors(awimbawe, theMightyJungle);

			List<String> notifications = new ArrayList<>();

			if (theMightyJungle.has("notifications")) {
				JsonArray jNotif = theMightyJungle.get("notifications").getAsJsonArray();
				notifications = new ArrayList<>(jNotif.size());

				for (JsonElement elem : jNotif) {
					notifications.add(elem.getAsString());
				}
			}

			JsonObject updates = theMightyJungle.get("updates").getAsJsonObject();

			List<User> users = new ArrayList<>();

			if (updates.has("list")) {
				JsonArray jUpdates = updates.getAsJsonArray("list");
				users = new ArrayList<>(jUpdates.size());

				for (JsonElement element : jUpdates) {
					JsonObject individual = element.getAsJsonObject();

					UUID uuid = Yootil.toUUID(individual.get("uuid").getAsString());

					users.add(new User(uuid, individual.get("username").getAsString()));
				}
			}

			return new ServerResponse<>(new CosmeticsUpdates(notifications, users, updates.get("timestamp").getAsLong()), awimbawe);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, awimbawe);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, awimbawe);
		}
	}

	// Client/ endpoints

	private ServerResponse<String> requestSet(SafeURL target) {
		this.urlLogger.accept(target.safeUrl());

		try (Response response = Response.get(target, this.timeout)) {
			JsonObject json = response.getAsJson();
			checkErrors(target, json);
			return new ServerResponse<>(json.get("success").getAsString(), target);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, target);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, target);
		}
	}

	private ServerResponse<Boolean> requestSetZ(SafeURL target) {
		this.urlLogger.accept(target.safeUrl());

		try (Response response = Response.get(target, this.timeout)) {
			JsonObject json = response.getAsJson();
			checkErrors(target, json);
			return new ServerResponse<>(json.get("success").getAsBoolean(), target);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, target);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, target);
		}
	}

	@Override
	public ServerResponse<Boolean> setCosmetic(CosmeticPosition position, String id, boolean requireOfficial) {
		SafeURL target = create("/client/setcosmetic?type=" + position.getUrlString() + "&id=" + id + (requireOfficial ? "&requireofficial" : ""), OptionalLong.empty());
		return requestSetZ(target);
	}

	@Override
	public ServerResponse<Boolean> setCosmeticStatus(CosmeticType<?> type, String id, UploadState state, String reason) throws IllegalArgumentException {
		if (state == UploadState.UNKNOWN) {
			throw new IllegalArgumentException("Cannot set cosmetic status to \"Unknown\"");
		}

		SafeURL target = create("/client/cosmeticstatus?type=" + type.getUrlString() + "&id=" + id + "&value=" + state.getId() + "&reason=" + Yootil.base64(reason), OptionalLong.empty());
		return requestSetZ(target);
	}

	@Override
	public ServerResponse<Boolean> updateExtraInfo(CosmeticType<?> type, String cosmeticId, int extraInfo) {
		SafeURL target = create("/client/modifyextrainfo?type=" + type.getUrlString() + "&id=" + cosmeticId + "&extrainfo=" + extraInfo, OptionalLong.empty());
		return requestSetZ(target);
	}

	@Override
	public ServerResponse<String> setLore(LoreType type, String lore) throws IllegalArgumentException {
		if (type == LoreType.DISCORD || type == LoreType.TWITCH) throw new IllegalArgumentException("Invalid lore type for setLore(LoreType, String): " + type);

		SafeURL target = create("/client/setlore?type=" + type.toString().toLowerCase(Locale.ROOT) + "&value=" + Yootil.base64(Yootil.urlEncode(lore)), OptionalLong.empty());
		return requestSet(target);
	}

	@Override
	public ServerResponse<String> removeLore() {
		return this.setLore(LoreType.NONE, "");
	}

	@Override
	public ServerResponse<Boolean> setPanorama(int id) {
		SafeURL target = create("/client/setpanorama?panorama=" + id, OptionalLong.empty());
		return requestSetZ(target);
	}

	@Override
	public ServerResponse<Map<String, CapeDisplay>> setCapeServerSettings(Map<String, CapeDisplay> settings) {
		SafeURL target = create("/client/capesettings?" + settings.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue().id).collect(Collectors.joining("&")), OptionalLong.empty());
		this.urlLogger.accept(target.safeUrl());

		try (Response response = Response.get(target, this.timeout)) {
			JsonObject obj = response.getAsJson();
			checkErrors(target, obj);

			return new ServerResponse<>(Yootil.mapObject(obj.get("success").getAsJsonObject(), element -> CapeDisplay.byId(element.getAsInt())), target);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, target);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, target);
		}
	}

	@Override
	public ServerResponse<Boolean> updateUserSettings(Map<String, Object> settings) {
		SafeURL target = create("/v2/client/updatesettings?" + settings.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&")), OptionalLong.empty());
		return requestSetZ(target);
	}

	@Override
	public ServerResponse<Boolean> updateIconSettings(IconSettings iconSettings) {
		Map<String, Object> settings = new HashMap<>();
		settings.put("iconsettings", iconSettings.packToInt());
		return this.updateUserSettings(settings);
	}

	@Override
	public ServerResponse<String> uploadCape(String name, String base64Image, int frameDelay) throws IllegalArgumentException {
		if (frameDelay < 0 || frameDelay > 500) throw new IllegalArgumentException("Frame delay must be between 0 and 500 (inclusive)");
		if (frameDelay % 50 != 0) throw new IllegalArgumentException("Frame delay must be a multiple of 50");

		SafeURL target = create("/client/uploadcloak", OptionalLong.empty());
		this.urlLogger.accept(target.safeUrl() + " (POST)");

		try (Response response = Response.post(target)
				.set("name", name)
				.set("image", base64Image)
				.set("extrainfo", frameDelay)
				.submit()) {
			JsonObject obj = response.getAsJson();
			checkErrors(target, obj);

			return new ServerResponse<>(obj.get("success").getAsString(), target);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, target);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, target);
		}
	}

	@Override
	public ServerResponse<String> uploadModel(CosmeticType<Model> type, String name, String base64Texture, JsonObject model, int flags) {
		SafeURL target = create("/client/upload" + type.getUrlString(), OptionalLong.empty());
		this.urlLogger.accept(target.safeUrl() + " (POST)");

		try (Response response = Response.post(target)
				.set("name", name)
				.set("image", base64Texture)
				.set("model", model.toString())
				.set("extrainfo", flags)
				.submit()) {
			JsonObject obj = response.getAsJson();
			checkErrors(target, obj);

			return new ServerResponse<>(obj.get("success").getAsString(), target);
		}
		catch (IOException ie) {
			return new ServerResponse<>(ie, target);
		}
		catch (RuntimeException e) {
			return new ServerResponse<>(e, target);
		}
	}

	private SafeURL createLimited(String target) {
		if (this.limitedToken != null) return SafeURL.of(this.apiHostProvider.getFastInsecureUrl() + target + (target.indexOf('?') == -1 ? "?" : "&") + "timestamp=" + System.currentTimeMillis(), this.limitedToken);
		else return create(target, OptionalLong.empty());
	}

	/**
	 * Create a fully authenticated request url to the cosmetica server. If no full token is provided, token will be left empty.
	 * @param target the target endpoint, starting with, e.g., /client/... or /get/..., and including url parameters.
	 * @param timestamp the timestamp, if manually setting.
	 * @return the url to request to.
	 */
	private SafeURL create(String target, OptionalLong timestamp) {
		if (this.masterToken != null) return SafeURL.of(this.apiHostProvider.getSecureUrl() + target + (target.indexOf('?') == -1 ? "?" : "&") + "timestamp=" + timestamp.orElseGet(System::currentTimeMillis), this.masterToken);
		else return SafeURL.of(this.apiHostProvider.getSecureUrl() + target + (target.indexOf('?') == -1 ? "?" : "&") + "timestamp=" + timestamp.orElseGet(System::currentTimeMillis));
	}

	private SafeURL createMinimalLimited(String target) {
		if (this.limitedToken != null) return SafeURL.of(this.apiHostProvider.getFastInsecureUrl() + target + (target.indexOf('?') == -1 ? "?" : "&") + "timestamp=" + System.currentTimeMillis(), this.limitedToken);
		else return createMinimal(target, OptionalLong.empty());
	}

	/**
	 * Create a fully authenticated request url to the cosmetica server. If no full token is provided, <b>no token will be provided</b>.
	 * @param target the target endpoint, starting with, e.g., /client/... or /get/..., and including url parameters.
	 * @param timestamp the timestamp, if manually setting.
	 * @return the url to request to.
	 * @apiNote Use this where both authenticated and non-authenticated functionality can be provided, and an empty token isn't treated as an unauthenticated request.
	 */
	private SafeURL createMinimal(String target, OptionalLong timestamp) {
		if (this.masterToken != null) return SafeURL.of(this.apiHostProvider.getSecureUrl() + target + (target.indexOf('?') == -1 ? "?" : "&") + "timestamp=" + timestamp.orElseGet(System::currentTimeMillis), this.masterToken);
		else return SafeURL.direct(this.apiHostProvider.getSecureUrl() + target + (target.indexOf('?') == -1 ? "?" : "&") + "timestamp=" + timestamp.orElseGet(System::currentTimeMillis));
	}

	private SafeURL createTokenless(String target, OptionalLong timestamp) {
		return SafeURL.of(this.apiHostProvider.getSecureUrl() + target + (target.indexOf('?') == -1 ? "?" : "&") + "timestamp=" + timestamp.orElseGet(System::currentTimeMillis));
	}

	@Override
	public void setUrlLogger(Consumer<String> urlLogger) {
		this.urlLogger = urlLogger;
	}

	@Override
	public void setRequestTimeout(int timeout) {
		this.timeout = timeout;
	}

	@Override
	public void setForceHttps(boolean forceHttps) {
		this.apiHostProvider.setForceHttps(forceHttps);
	}

	@Override
	public boolean isFullyAuthenticated() {
		return this.masterToken != null;
	}

	@Override
	public boolean isAuthenticated() {
		return this.isFullyAuthenticated() || this.limitedToken != null;
	}

	@Override
	public boolean isHttpsForced() {
		return this.forceHttps();
	}

	/**
	 * Use this method if you're cringe.<br>
	 * (exists to stop reflection being necessary for the few times it's justified to manually get the token rather than going through the api)
	 * @return the master token on this instance
	 */
	public String getMasterToken() {
		return this.masterToken;
	}

	// Global Force Https
	private static boolean enforceHttpsGlobal;

	public static void setDefaultForceHttps(boolean forceHttps) {
		enforceHttpsGlobal = forceHttps;
		// update api host provider too
		if (apiHostProviderTemplate != null) apiHostProviderTemplate.setForceHttps(forceHttps);
	}

	public static boolean getDefaultForceHttps() {
		return enforceHttpsGlobal;
	}

	// Initialisation Stuff

	private static HostProvider apiHostProviderTemplate;
	private static String authApiServerHost;

	private static String websiteHost;
	private static String authServerHost;

	private static String message;

	private static File apiCache;

	public static String getMessage() {
		return message;
	}

	public static String getWebsite() {
		return websiteHost;
	}

	public static CosmeticaAPI fromTempToken(String tempToken, UUID uuid, @Nullable String client) throws IllegalStateException, IOException, FatalServerErrorException {
		retrieveAPIIfNoneCached();
		return new CosmeticaWebAPI(uuid, tempToken, client);
	}

	public static CosmeticaAPI fromMinecraftToken(String minecraftToken, String username, UUID uuid, @Nullable String client) throws IllegalStateException, IOException, FatalServerErrorException {
		retrieveAPIIfNoneCached();

		byte[] publicKey;

		// https://wiki.vg/Protocol_Encryption
		try (Response response = Response.get(authApiServerHost + "/key")) {
			publicKey = response.getAsByteArray();
		}

		byte[] sharedSecret = Yootil.randomBytes(16);
		String hash = Yootil.hash("".getBytes(StandardCharsets.US_ASCII), sharedSecret, publicKey);

		// authenticate with minecraft
		try (Response response = Response.postJson("https://sessionserver.mojang.com/session/minecraft/join")
				.set("accessToken", minecraftToken)
				.set("selectedProfile", uuid.toString().replaceAll("-", ""))
				.set("serverId", hash)
				.submit()) {
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		// Exchange Tokens with Cosmetica
		try (Response response = Response.postJson(authApiServerHost + "/verify")
				.set("secret", new String(Base64.getEncoder().encode(sharedSecret)))
				.set("username", username)
				.submit()) {
			JsonObject data = response.getAsJson();
			checkErrors(SafeURL.direct(authApiServerHost + "/verify"), data);

			return new CosmeticaWebAPI(uuid, data.get("token").getAsString(), client);
		}
	}

	public static CosmeticaAPI fromTokens(@Nullable String masterToken, @Nullable String limitedToken) throws IllegalStateException {
		retrieveAPIIfNoneCached();
		return new CosmeticaWebAPI(masterToken, limitedToken);
	}

	public static CosmeticaAPI newUnauthenticatedInstance() throws IllegalStateException {
		retrieveAPIIfNoneCached();
		return new CosmeticaWebAPI(null, null);
	}

	@Nullable
	public static String getApiServerHost(boolean requireResult) throws IllegalStateException {
		if (requireResult) retrieveAPIIfNoneCached();
		return apiHostProviderTemplate == null ? null : apiHostProviderTemplate.getSecureUrl();
	}

	@Nullable
	public static String getFastInsecureApiServerHost(boolean requireResult) throws IllegalStateException {
		if (requireResult) retrieveAPIIfNoneCached();
		return apiHostProviderTemplate == null ? null : apiHostProviderTemplate.getFastInsecureUrl();
	}

	@Nullable
	public static String getAuthApiServerHost(boolean requireResult) throws IllegalStateException {
		if (requireResult) retrieveAPIIfNoneCached();
		return authApiServerHost;
	}

	public static void setAPICache(File api) {
		apiCache = api;
	}

	private static void retrieveAPIIfNoneCached() throws IllegalStateException {
		if (apiHostProviderTemplate == null) { // if this sequence has not already been initiated
			final String apiGetHost = enforceHttpsGlobal ? "https://cosmetica.cc/getapi" : "http://cosmetica.cc/getapi";

			String apiGetData = null;
			Exception eStored = new NullPointerException("Response succeeded but cosmetica.cc/getapi entity was null"); // in case response succeeds but somehow get data is null

			try (Response apiGetResponse = Response.get(apiGetHost)) {
				apiGetData = apiGetResponse.getAsString();
			} catch (Exception e) {
				System.err.println("(Cosmetica API) Connection error to cosmetica.cc/getapi. Trying to retrieve from local cache...");
				eStored = e;
			}

			if (apiCache != null) apiGetData = Yootil.loadOrCache(apiCache, apiGetData);

			if (apiGetData == null) {
				throw new IllegalStateException("Could not receive Cosmetica API host", eStored);
			}

			JsonObject data = new JsonParser().parse(apiGetData).getAsJsonObject();
			apiHostProviderTemplate = new HostProvider(data.get("api").getAsString(), enforceHttpsGlobal);
			authApiServerHost = data.get("auth-api").getAsString();
			websiteHost = data.get("website").getAsString();
			message = data.get("message").getAsString();
		}
	}

	private static void checkErrors(SafeURL url, JsonObject response) {
		if (response.has("error")) {
			throw new CosmeticaAPIException(url, response.get("error").getAsString());
		}
	}

	private static JsonArray getAsArray(SafeURL url, JsonElement element) throws CosmeticaAPIException {
		if (element.isJsonObject()) {
			checkErrors(url, element.getAsJsonObject());
		}

		return element.getAsJsonArray();
	}
}
