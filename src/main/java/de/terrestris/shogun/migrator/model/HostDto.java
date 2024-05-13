package de.terrestris.shogun.migrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HostDto {

  public HostDto(String hostname, String username, String password) {
    this.hostname = hostname;
    this.username = username;
    this.password = password;
  }

  private String hostname;

  private String username;

  private String password;

  private String clientId;

  private String token;

}
