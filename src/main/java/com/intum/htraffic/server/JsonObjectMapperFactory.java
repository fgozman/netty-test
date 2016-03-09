package com.intum.htraffic.server;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonObjectMapperFactory {
	public static ObjectMapper getInstance(){
		ObjectMapper objMapper = new ObjectMapper();        		
		objMapper.setDateFormat(DateFormatterJson.createUTCDateFormat());
		objMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		objMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		objMapper.setSerializationInclusion(Include.NON_NULL);		
		objMapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
		
		return objMapper;
	}
}
