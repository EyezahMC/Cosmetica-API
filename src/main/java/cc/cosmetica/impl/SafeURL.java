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

package cc.cosmetica.impl;

/**
 * Safe URL thing for keeping tokens safe.
 */
record SafeURL(String url, String safeUrl) {
	static SafeURL of(String baseUrl, String token) {
		return new SafeURL(baseUrl + (baseUrl.contains("?") ? "&" : "?") + "token=" + token, baseUrl);
	}

	static SafeURL of(String baseUrl) {
		return new SafeURL(baseUrl + (baseUrl.contains("?") ? "&" : "?") + "token=", baseUrl);
	}

	@Override
	public String toString() {
		return "SafeURL{" + this.safeUrl + "}";
	}
}
