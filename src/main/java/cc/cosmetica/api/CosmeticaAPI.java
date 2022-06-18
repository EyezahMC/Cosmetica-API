/**
 * Copyright (c) 2022 EyezahMC
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

package cc.cosmetica.api;

import cc.cosmetica.impl.CosmeticaWebAPI;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A general interface with the Cosmetica Web API. Methods that throw IOException typically throw it when there is an issue contacting the API server, and {@link CosmeticaAPIException} if the api server can be contacted, but returns an error.
 */
public interface CosmeticaAPI {
	//////////////////////
	//  Web-API Methods //
	//////////////////////

	/**
	 * Sends a version check request to the cosmetica servers and retrieves text to give to the user if there is an update, otherwise returns an empty string.
	 * @param minecraftVersion the version of the cosmetica mod.
	 * @param cosmeticaVersion the minecraft version, duh. {@code (Use SharedConstants.getCurrentVersion().getId()} if you're a minecraft mod using this API).
	 * @return an object with a message sent by the API if the cosmetica version is outdated or old enough that it may not function correctly.
	 */
	ServerResponse<VersionInfo> checkVersion(String minecraftVersion, String cosmeticaVersion);

	/**
	 * Exchanges the auth token in this API instance for a 'master' and 'limited' token, if it does not already have them stored.
	 * @param uuid the UUID of the player we are requesting to have cosmetica api access tokens for.
	 * @return relevant login information.
	 * @throws IllegalStateException if this instance was created without an auth token (i.e directly with api tokens), as there is nothing to exchange.
	 */
	ServerResponse<LoginInfo> exchangeTokens(UUID uuid) throws IllegalStateException;

	/**
	 * Head on the safari to check out the lion king's new cosmetics! I mean, uh, ping this to get updates on any cosmetic changes you may have missed in the last 4 minutes from users on the server you're on, and allow other cosmetica users on the same server to receive cosmetics updates for you.<br>
	 * If you provide a timestamp of 0, the endpoint will not send any users nor notifications, but instead only respond with a timestamp to use next time. The cosmetica mod calls this endpoint with a timestamp of 0 upon first joining a server to get its initial timestamp for this server.
	 * @param serverAddress the address of the minecraft server you're on. This {@link InetSocketAddress} must have an IP and port associated.
	 * @return the updates from this endpoint.
	 * @throws IllegalArgumentException if the InetSocketAddress does not have an IP and port.
	 * @apiNote the response to this endpoint provides a timestamp to use when you next call it from the same server.
	 */
	ServerResponse<CosmeticsUpdates> everyThirtySecondsInAfricaHalfAMinutePasses(InetSocketAddress serverAddress, long timestamp) throws IllegalArgumentException;

	/**
	 * Retrieves user info from the api server via either the UUID, username, or both. UUID is used preferentially.
	 * @param uuid the uuid of the player to retrieve data of.
	 * @param username the username of the player to retrieve data of.
	 * @return a representation of the cosmetics data of the given player.
	 * @throws IllegalArgumentException if both {@code uuid} and {@code username} are null.
	 */
	ServerResponse<UserInfo> getUserInfo(@Nullable UUID uuid, @Nullable String username) throws IllegalArgumentException;

	/**
	 * Retrieves the settings of the user associated with the token and some basic data.
	 * @return the user's settings, as JSON.
	 */
	ServerResponse<UserSettings> getUserSettings();

	/**
	 * Gets a page of 16 cosmetics, sorted by upload date.
	 * @param type the type of cosmetic to search for.
	 * @param page the page number to browse.
	 * @return a page of cosmetics.
	 */
	default <T extends CustomCosmetic> ServerResponse<CosmeticsPage<T>> getRecentCosmetics(CosmeticType<T> type, int page) {
		return getRecentCosmetics(type, page, 16, Optional.empty());
	}

	/**
	 * Gets a page of cosmetics that match the given query, sorted by upload date.
	 * @param type the type of cosmetic to search for.
	 * @param page the page number to browse.
	 * @param pageSize how large each page should be. For example, the desktop website uses 16, whereas mobile uses 8.
	 * @param query the search term. If a query is provided, 'official' cosmetica cosmetics may be returned in addition to user-uploaded cosmetics.
	 * @return a page of cosmetics sorted by upload date.
	 */
	<T extends CustomCosmetic> ServerResponse<CosmeticsPage<T>> getRecentCosmetics(CosmeticType<T> type, int page, int pageSize, Optional<String> query);

	/**
	 * Gets a page of 16 cosmetics sorted by popularity.
	 * @param page the page number to browse.
	 * @return a page of cosmetics sorted by popularity.
	 */
	default ServerResponse<CosmeticsPage<CustomCosmetic>> getPopularCosmetics(int page) {
		return getPopularCosmetics(page, 16);
	}

	/**
	 * Gets a page of official ("system") cosmetics.
	 * @param page the page number to browse.
	 * @param pageSize how large each page should be. For example, the desktop website uses 16, whereas mobile uses 8.
	 * @return a page of official cosmetics.
	 */
	ServerResponse<CosmeticsPage<CustomCosmetic>> getOfficialCosmetics(int page, int pageSize);

	/**
	 * Gets a page of 16 official ("system") cosmetics.
	 * @param page the page number to browse.
	 * @return a page of official cosmetics.
	 */
	default ServerResponse<CosmeticsPage<CustomCosmetic>> getOfficialCosmetics(int page) {
		return getOfficialCosmetics(page, 16);
	}

	/**
	 * Gets a page of cosmetics sorted by popularity.
	 * @param page the page number to browse.
	 * @param pageSize how large each page should be. For example, the desktop website uses 16, whereas mobile uses 8.
	 * @return a page of cosmetics sorted by popularity.
	 */
	ServerResponse<CosmeticsPage<CustomCosmetic>> getPopularCosmetics(int page, int pageSize);

	/**
	 * Gets the list of available lore of that type the user can set to.
	 * @param type a type of lore that uses a list of options. Namely {@link LoreType#PRONOUNS} or {@link LoreType#TITLES}.
	 * @return a list of lore strings the user can select from.
	 * @throws IllegalArgumentException if the lore type does not have an associated lore list (if it's not "Pronouns" or "Titles").
	 */
	ServerResponse<List<String>> getLoreList(LoreType type) throws IllegalArgumentException;

	/**
	 * Gets a cosmetic from the cosmetica servers.
	 * @param type the type of cosmetic.
	 * @param id the id of the cosmetic.
	 * @return an object representing the cosmetic.
	 */
	<T extends CustomCosmetic> ServerResponse<T> getCosmetic(CosmeticType<T> type, String id);

	/**
	 * Gets the list of panoramas the user can select from. The cosmetica website displays a panorama behind the user on their user page.
	 *
	 * @return a list of panoramas the user can use.
	 * @apiNote if no token is given, returns the panoramas all users can select.
	 */
	ServerResponse<List<Panorama>> getPanoramas();

	/**
	 * Sets the cosmetic for this user.
	 * @param type the type of cosmetic to set.
	 * @param id the id of the cosmetic.
	 * @return true if successful. Otherwise the server response will have an error.
	 * @apiNote requires full authentication (a master token).
	 */
	ServerResponse<Boolean> setCosmetic(CosmeticType<?> type, String id);

	/**
	 *
	 * @param type the type of lore to be set. Can be either {@link LoreType#PRONOUNS} or {@link LoreType#TITLES}.
	 * @param lore the lore string to set as the lore.
	 * @return the new lore string of the player (including colour codes) if successful. Otherwise the server response will have an error.
	 * @throws IllegalArgumentException if the lore type cannot be set through this endpoint (if it's not "Pronouns" or "Titles").
	 * @apiNote requires full authentication (a master token).
	 */
	ServerResponse<String> setLore(LoreType type, String lore) throws IllegalArgumentException;

	/**
	 * Sets the panorama for this user.
	 * @param id the id of the panorama to set. Panorama ids this user can use can be gotten with {@link CosmeticaAPI#getPanoramas}.
	 * @return true if successful. Otherwise the server response will have an error.
	 * @apiNote requires full authentication (a master token).
	 */
	ServerResponse<Boolean> setPanorama(int id);

	/**
	 * Sets how cosmetica should handle each cape service for this user. In addition, <b>ANY CAPE NOT SPECIFIED IS RESET TO THE DEFAULT VALUE. You should call {@link CosmeticaAPI#getUserSettings()} at least ONCE before calling this to get the current settings of the user! You have been warned.</b>
	 * @param settings
	 * @return true if successful. Otherwise the server response will have an error.
	 * @apiNote requires full authentication (a master token).
	 */
	ServerResponse<Boolean> setCapeServerSettings(Map<String, CapeDisplay> settings);

	/**
	 * Updates the specified settings for the user. You do not need to specify every setting, unlike {@link CosmeticaAPI#setCapeServerSettings(Map)}
	 * @param settings
	 * @return true if successful. Otherwise the server response will have an error.
	 * @apiNote requires full authentication (a master token).
	 */
	ServerResponse<Boolean> updateUserSettings(Map<String, Object> settings);

	/**
	 * Uploads a cape to the server under this account.
	 * @param name the name of the cape to upload.
	 * @param base64Image the image in base64 form. Ensure it is a png that starts with "data:image/png;base64,"
	 * @return the id of the cape if successful. Otherwise the server response will have an error.
	 * @apiNote requires full authentication (a master token).
	 */
	ServerResponse<String> uploadCape(String name, String base64Image);

	/**
	 * Uploads a model-based cosmetic to the server under this account.
	 * @param type the type of cosmetic to upload.
	 * @param name the name of the cosmetic to upload.
	 * @param base64Texture the 32x32 texture in base64 form. Ensure it is a png that starts with "data:image/png;base64,"
	 * @param model the json model to upload
	 * @return the id of the cosmetic if successful. Otherwise the server response will have an error.
	 * @apiNote requires full authentication (a master token).
	 */
	ServerResponse<String> uploadModel(CosmeticType<Model> type, String name, String base64Texture, JsonObject model);

	///////////////////////////
	//   Non-Web-API Methods //
	///////////////////////////

	/**
	 * Pass a consumer to be invoked with the URL whenever a URL is contacted. This can be useful for debug logging purposes.
	 * @param logger the logger to pass.
	 */
	void setUrlLogger(@Nullable Consumer<String> logger);

	/**
	 * @return whether this cosmetica api instance has a master API token.
	 */
	boolean isFullyAuthenticated();

	/**
	 * @return whether this cosmetica api instance has any API token (master or limited).
	 */
	boolean isAuthenticated();

	/**
	 * Sets a new authentication token for this instance to use. This resets the master and limited tokens stored on this instance, so {@link CosmeticaAPI#exchangeTokens(UUID)} must be called after this.
	 */
	void setAuthToken(String authenticationToken);

	/**
	 * Create an instance with which to access the cosmetica web api via one token.
	 * @param token a cosmetica token. Can be a master token, limited token, or cosmetica authentication token.
	 * @return an instance of the cosmetica web api, configured with the given token. The instance will behave in the following way for each case:<br>
	 * <h2>Master Token</h2>
	 *   Uses only the master token for an account. This instance will only make requests on https, unlike other instances which make non-sensitive "get" requests under http for speed.
	 * <h2>Limited Token</h2>
	 *   Uses only a cosmetica 'limited' or 'get' token, a special token for use over HTTP which only has access to specific "get" endpoints. This instance will only make requests on http, so is less secure.
	 * <h2>Temporary Token</h2>
	 *   Uses a Cosmetica temporary authentication token: a special token used as an intermediate step between initial authentication and receiving your two api tokens. After creating an instance with the authentication token, {@linkplain CosmeticaAPI#exchangeTokens(UUID) this can then be exchanged with the cosmetica api} for a valid new master and limited token with which this cosmetica api instance will be configured.
	 * @throws IllegalStateException if an api instance cannot be retrieved.
	 * @throws IllegalArgumentException if the token given does not match the format for any of the 3 token types.
	 */
	static CosmeticaAPI fromToken(String token) throws IllegalStateException, IllegalArgumentException {
		switch (token.charAt(0)) {
		case 'm':
			return CosmeticaWebAPI.fromTokens(token, null);
		case 'l':
			return CosmeticaWebAPI.fromTokens(null, token);
		case 't':
			return CosmeticaWebAPI.fromTempToken(token);
		default:
			throw new IllegalArgumentException("Cannot determine type of token " + token);
		}
	}

	/**
	 * Login to Cosmetica with a minecraft account's token, username, and UUID directly. The resulting {@link CosmeticaAPI} instance will be fully authenticated with a master and limited token, as with using a temporary token in {@link CosmeticaAPI#fromToken(String)}.
	 * @param minecraftToken the user's minecraft authentication token.
	 * @param username the user's username.
	 * @param uuid the user's UUID.
	 * @return an instance of the cosmetica web api, configured with the given account.
	 * @throws IllegalStateException if an api instance cannot be retrieved.
	 * @throws IOException if there's an I/O exception while contacting the minecraft auth servers or cosmetica servers to authenticate the user.
	 * @throws FatalServerErrorException if there is a 5XX error while contacting the servers.
	 */
	static CosmeticaAPI fromMinecraftToken(String minecraftToken, String username, UUID uuid) throws IllegalStateException, IOException, FatalServerErrorException {
		return CosmeticaWebAPI.fromMinecraftToken(minecraftToken, username, uuid);
	}

	/**
	 * @param masterToken the cosmetica master token.
	 * @param limitedToken the cosmetica 'limited' or 'get' token, a special token for use over HTTP which only has access to specific "get" endpoints.
	 * @return an instance of the cosmetica web api, configured with the given tokens.
	 * @throws IllegalStateException if an api instance cannot be retrieved.
	 */
	static CosmeticaAPI fromTokens(String masterToken, String limitedToken) throws IllegalStateException {
		return CosmeticaWebAPI.fromTokens(masterToken, limitedToken);
	}

	/**
	 * Creates a new instance which is not authenticated. The provided instance will be very limited in what endpoints it can call.
	 * @return an instance of the cosmetica web api with no associated token.
	 * @throws IllegalStateException if an api instance cannot be retrieved.
	 */
	static CosmeticaAPI newUnauthenticatedInstance() throws IllegalStateException {
		return CosmeticaWebAPI.newUnauthenticatedInstance();
	}

	/**
	 * Sets the file to cache the API endpoints to, and retrieve from in case of the Github CDN or "getapi" endpoints go offline.
	 */
	static void setAPICaches(File apiCache, File apiGetCache) {
		CosmeticaWebAPI.setAPICaches(apiCache, apiGetCache);
	}

	/**
	 * Get the message retrieved once a {@link CosmeticaAPI} instance is retrieved from {@link CosmeticaAPI#fromToken}, {@link CosmeticaAPI#fromMinecraftToken(String, String, UUID)}, {@link CosmeticaAPI#fromTokens}, or another method that forces initial API data to be fetched is called.
	 */
	@Nullable
	static String getMessage() {
		return CosmeticaWebAPI.getMessage();
	}

	/**
	 * Get the auth server url. Will force the initial API data to be fetched if it is not.
	 */
	static String getAuthServer() {
		return CosmeticaWebAPI.getAuthServerHost(true);
	}

	/**
	 * Get the auth-api server url. Will force the initial API data to be fetched if it is not.
	 */
	static String getAuthApiServer() {
		return CosmeticaWebAPI.getAuthApiServerHost(true);
	}

	/**
	 * Get the cosmetica website url, retrieved once a {@link CosmeticaAPI} instance is retrieved from {@link CosmeticaAPI#fromToken}, {@link CosmeticaAPI#fromMinecraftToken(String, String, UUID)}, {@link CosmeticaAPI#fromTokens}, or another method that forces initial API data to be fetched is called.
	 */
	@Nullable
	static String getWebsite() {
		return CosmeticaWebAPI.getWebsite();
	}

	/**
	 * Get the cosmetica api server url being used, retrieved once a {@link CosmeticaAPI} instance is retrieved from {@link CosmeticaAPI#fromToken}, {@link CosmeticaAPI#fromMinecraftToken(String, String, UUID)}, {@link CosmeticaAPI#fromTokens}, or another method that forces initial API data to be fetched is called.
	 */
	@Nullable
	static String getAPIServer() {
		return CosmeticaWebAPI.getApiServerHost(false);
	}

	/**
	 * Get the cosmetica api server url being used as an insecure http:// url, retrieved once a {@link CosmeticaAPI} instance is retrieved from {@link CosmeticaAPI#fromToken}, {@link CosmeticaAPI#fromMinecraftToken(String, String, UUID)}, {@link CosmeticaAPI#fromTokens}, or another method that forces initial API data to be fetched is called.
	 */
	@Nullable
	static String getHttpAPIServer() {
		return CosmeticaWebAPI.getFastInsecureApiServerHost(false);
	}
}
