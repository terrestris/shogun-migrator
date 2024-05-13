package de.terrestris.shogun.migrator.spi;

import com.fasterxml.jackson.databind.JsonNode;

public interface LayerPostProcessor {

  void postprocess(JsonNode config);

}
