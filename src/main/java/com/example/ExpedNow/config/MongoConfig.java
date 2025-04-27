package com.example.ExpedNow.config;

import com.example.ExpedNow.models.enums.Role;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();

        // Role converters
        converters.add(new StringToRoleConverter());
        converters.add(new RoleToStringConverter());
        converters.add(new ArrayListToRoleSetConverter());

        // DateTime converters
        converters.add(localDateTimeToDateConverter());
        converters.add(dateToLocalDateTimeConverter());

        return new MongoCustomConversions(converters);
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
        converter.setCustomConversions(customConversions());
        converter.afterPropertiesSet();
        return new MongoTemplate(mongoDbFactory, converter);
    }

    public Converter<LocalDateTime, Date> localDateTimeToDateConverter() {
        return new Converter<LocalDateTime, Date>() {
            @Override
            public Date convert(LocalDateTime source) {
                return Date.from(source.atZone(ZoneId.systemDefault()).toInstant());
            }
        };
    }

    public Converter<Date, LocalDateTime> dateToLocalDateTimeConverter() {
        return new Converter<Date, LocalDateTime>() {
            @Override
            public LocalDateTime convert(Date source) {
                return source.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        };
    }
}