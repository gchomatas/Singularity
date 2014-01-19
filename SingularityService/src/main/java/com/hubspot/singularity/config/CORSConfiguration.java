package com.hubspot.singularity.config;

import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CORSConfiguration extends Configuration {

  @NotNull
  @JsonProperty
  private String prefix = "/*";

  @NotNull
  @JsonProperty
  private String[] allowedOrigins = new String[] { "*" };

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public String[] getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(String[] allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

}
