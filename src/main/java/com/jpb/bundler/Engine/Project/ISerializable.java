package com.jpb.bundler.Engine.Project;

import org.json.JSONObject;

public interface ISerializable
{
	default JSONObject Serialize()
	{
		return OnSerialize();
	}

	default void Deserialize(JSONObject _JSON)
	{
		OnDeserialize(_JSON);
	}

	JSONObject OnSerialize();

	void OnDeserialize(JSONObject _JSON);
}
