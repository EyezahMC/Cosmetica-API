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

package cc.cosmetica.api;

import java.util.Objects;

/**
 * Information retrieved on login.
 */
public final class LoginInfo {
	public LoginInfo(boolean isNewPlayer, boolean hasSpecialCape) {
		this.isNewPlayer = isNewPlayer;
		this.hasSpecialCape = hasSpecialCape;
	}

	private final boolean isNewPlayer;
	private final boolean hasSpecialCape;

	public boolean isNewPlayer() {
		return isNewPlayer;
	}

	public boolean hasSpecialCape() {
		return hasSpecialCape;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		LoginInfo that = (LoginInfo) obj;
		return this.isNewPlayer == that.isNewPlayer &&
				this.hasSpecialCape == that.hasSpecialCape;
	}

	@Override
	public int hashCode() {
		return Objects.hash(isNewPlayer, hasSpecialCape);
	}

	@Override
	public String toString() {
		return "LoginInfo[" +
				"isNewPlayer=" + isNewPlayer + ", " +
				"hasSpecialCape=" + hasSpecialCape + ']';
	}

}
