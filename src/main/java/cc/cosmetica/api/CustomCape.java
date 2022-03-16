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

/**
 * Represents a cape retrieved specifically from the cosmetica cape servers (as opposed to Optifine, Mantle, etc.)
 */
public interface CustomCape extends Cape {
	/**
	 * @return the owner of this model. Can be a hyphenless UUID of a player, or "system" (e.g. for the starter capes).
	 */
	String getOwner();

	/**
	 * @return
	 */
	String getName();
}
