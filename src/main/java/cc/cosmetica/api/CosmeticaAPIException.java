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

import cc.cosmetica.util.SafeURL;

/**
 * Thrown when the cosmetica web api returns a response, but that response is an error.
 */
public class CosmeticaAPIException extends RuntimeException {
	public CosmeticaAPIException(SafeURL url, String message) {
		this("API server request to " + url.safeUrl() + " responded with error: " + message);
	}

	public CosmeticaAPIException(String message) {
		super(message);
	}
}
