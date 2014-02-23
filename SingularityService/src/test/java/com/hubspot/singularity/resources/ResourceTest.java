package com.hubspot.singularity.resources;

import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider;
import io.dropwizard.logging.LoggingFactory;

import java.util.Map;
import java.util.Set;

import javax.validation.Validation;
import javax.validation.Validator;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.LowLevelAppDescriptor;

public class ResourceTest {

  static {
    LoggingFactory.bootstrap();
  }

  public static class Builder {

    private final Set<Object> singletons = Sets.newHashSet();
    private final Set<Class<?>> providers = Sets.newHashSet();
    private final Map<String, Boolean> features = Maps.newHashMap();
    private final Map<String, Object> properties = Maps.newHashMap();
    private ObjectMapper mapper = Jackson.newObjectMapper();
    private Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    public Builder setMapper(ObjectMapper mapper) {
      this.mapper = mapper;
      return this;
    }

    public Builder setValidator(Validator validator) {
      this.validator = validator;
      return this;
    }

    public Builder addResource(Object resource) {
      singletons.add(resource);
      return this;
    }

    public Builder addProvider(Class<?> klass) {
      providers.add(klass);
      return this;
    }

    public Builder addProvider(Object provider) {
      singletons.add(provider);
      return this;
    }

    public Builder addFeature(String feature, Boolean value) {
      features.put(feature, value);
      return this;
    }

    public Builder addProperty(String property, Object value) {
      properties.put(property, value);
      return this;
    }

    public ResourceTest build() {
      return new ResourceTest(singletons, providers, features, properties, mapper, validator);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final Set<Object> singletons;
  private final Set<Class<?>> providers;
  private final Map<String, Boolean> features;
  private final Map<String, Object> properties;
  private final ObjectMapper mapper;
  private final Validator validator;

  private JerseyTest test;

  private ResourceTest(Set<Object> singletons, Set<Class<?>> providers, Map<String, Boolean> features, Map<String, Object> properties, ObjectMapper mapper, Validator validator) {
    this.singletons = singletons;
    this.providers = providers;
    this.features = features;
    this.properties = properties;
    this.mapper = mapper;
    this.validator = validator;

    this.test = createTest();
  }

  public Validator getValidator() {
    return validator;
  }

  public ObjectMapper getObjectMapper() {
    return mapper;
  }

  public Client client() {
    return test.client();
  }

  public JerseyTest getJerseyTest() {
    return test;
  }

  public JerseyTest createTest() {
    JerseyTest test = new JerseyTest() {
      @Override
      protected AppDescriptor configure() {
        final DropwizardResourceConfig config = DropwizardResourceConfig.forTesting(new MetricRegistry());
        for (Class<?> provider : providers) {
          config.getClasses().add(provider);
        }
        for (Map.Entry<String, Boolean> feature : features.entrySet()) {
          config.getFeatures().put(feature.getKey(), feature.getValue());
        }
        for (Map.Entry<String, Object> property : properties.entrySet()) {
          config.getProperties().put(property.getKey(), property.getValue());
        }
        config.getSingletons().add(new JacksonMessageBodyProvider(mapper, validator));
        config.getSingletons().addAll(singletons);
        return new LowLevelAppDescriptor.Builder(config).build();
      }
    };

    return test;
  }

}