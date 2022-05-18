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

import cc.cosmetica.api.Box;
import cc.cosmetica.api.CosmeticType;
import cc.cosmetica.api.CustomModel;
import cc.cosmetica.api.User;

import java.util.UUID;

class CustomModelImpl extends BaseModel implements CustomModel {
	CustomModelImpl(CosmeticType<?> type, String id, int flags, Box bounds,
					String model, String base64Texture, User owner, long uploadTime, boolean usesUVRotations) {
		super(id, flags, bounds);

		this.model = model;
		this.texture = base64Texture;
		this.owner = owner;
		this.uploadTime = uploadTime;
		this.usesUVRotations = usesUVRotations;
		this.type = type;
	}

	private final String model;
	private final String texture;
	private final User owner;
	private final long uploadTime;
	private final boolean usesUVRotations;
	private final CosmeticType<?> type;

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public String getTexture() {
		return this.texture;
	}

	@Override
	public User getOwner() {
		return this.owner;
	}

	@Override
	public long getUploadTime() {
		return this.uploadTime;
	}

	@Override
	public boolean usesUVRotations() {
		return this.usesUVRotations;
	}

	@Override
	public boolean isBuiltin() {
		return false;
	}

	@Override
	public CosmeticType<?> getType() {
		return this.type;
	}
}
