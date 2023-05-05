package de.terrestris.shogun.migrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HostDto {

  private String hostname;

  private String username;

  private String password;

}
