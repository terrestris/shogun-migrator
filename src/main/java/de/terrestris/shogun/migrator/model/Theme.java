package de.terrestris.shogun.migrator.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Theme {
  private String primaryColor;

  private String secondaryColor;

  private String complementaryColor;

  private String logoPath;

  private String faviconPath;
}
