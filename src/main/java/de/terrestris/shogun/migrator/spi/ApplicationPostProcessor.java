package de.terrestris.shogun.migrator.spi;

import com.fasterxml.jackson.databind.JsonNode;

public interface ApplicationPostProcessor {

  void postprocess(JsonNode config);

}
