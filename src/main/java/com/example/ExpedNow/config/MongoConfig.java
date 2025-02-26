package com.example.ExpedNow.config;

import com.example.ExpedNow.models.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new StringToRoleConverter(),
                new RoleToStringConverter(),
                new ArrayListToRoleSetConverter()
        ));
    }

    @ReadingConverter
    private static class StringToRoleConverter implements Converter<String, Role> {
        @Override
        public Role convert(String source) {
            return Role.valueOf(source);
        }
    }

    @WritingConverter
    private static class RoleToStringConverter implements Converter<Role, String> {
        @Override
        public String convert(Role source) {
            return source.name();
        }
    }

    @ReadingConverter
    private static class ArrayListToRoleSetConverter implements Converter<ArrayList, Set<Role>> {
        @Override
        public Set<Role> convert(ArrayList source) {
            Set<Role> roles = new HashSet<>();
            for (Object item : source) {
                if (item instanceof String) {
                    roles.add(Role.valueOf((String) item));
                }
            }
            return roles;
        }
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDbFactory, MongoMappingContext context) {
        MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(mongoDbFactory), context);
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return new MongoTemplate(mongoDbFactory, converter);
    }
}