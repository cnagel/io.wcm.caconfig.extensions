/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2017 wcm.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.wcm.caconfig.extensions.references.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.caconfig.management.ConfigurationManager;
import org.apache.sling.caconfig.management.ConfigurationResourceResolverConfig;
import org.apache.sling.caconfig.management.multiplexer.ConfigurationResourceResolvingStrategyMultiplexer;
import org.apache.sling.caconfig.spi.metadata.ConfigurationMetadata;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageFilter;
import com.day.cq.wcm.api.PageManager;
import com.day.cq.wcm.api.reference.ReferenceProvider;

/**
 * <p>
 * This implementation of {@link ReferenceProvider} allows to resolve references of a given {@link Resource} to
 * context-aware configurations.
 * </p>
 * <p>
 * This is for example used by ActivationReferenceSearchServlet to resolve referenced content of pages during activation
 * of a page using AEM sites. Returning the configurations allows the editor to activate them along with the page
 * referring to them.
 * </p>
 * <p>
 * This component can be disabled by configuration, but its enabled by default.
 * </p>
 */
@Component(service = ReferenceProvider.class)
@Designate(ocd = ConfigurationReferenceProvider.Config.class)
public class ConfigurationReferenceProvider implements ReferenceProvider {

  @ObjectClassDefinition(name = "wcm.io Context-Aware Configuration Reference Provider",
      description = "Allows to resolve references from resources to their Context-Aware configurations, for example during page activation.")
  static @interface Config {

    @AttributeDefinition(name = "Enabled",
        description = "Enable this reference provider.")
    boolean enabled() default true;
  }

  static final String REFERENCE_TYPE = "caconfig";

  @Reference
  private ConfigurationManager configurationManager;

  @Reference
  private ConfigurationResourceResolvingStrategyMultiplexer configurationResourceResolvingStrategy;

  @Reference
  private ConfigurationResourceResolverConfig configurationResourceResolverConfig;

  private boolean enabled;

  private static final Logger log = LoggerFactory.getLogger(ConfigurationReferenceProvider.class);

  @Activate
  protected void activate(Config config) {
    enabled = config.enabled();
  }

  @Deactivate
  protected void deactivate() {
    enabled = false;
  }

  @Override
  public List<com.day.cq.wcm.api.reference.Reference> findReferences(Resource resource) {
    if (!enabled) {
      return Collections.emptyList();
    }

    PageManager pageManager = resource.getResourceResolver().adaptTo(PageManager.class);
    Set<String> configurationNames = configurationManager.getConfigurationNames();
    List<com.day.cq.wcm.api.reference.Reference> references = new ArrayList<>(configurationNames.size());
    Collection<String> configurationBuckets = configurationResourceResolverConfig.configBucketNames();

    for (String configurationName : configurationNames) {
      ConfigurationMetadata configurationMetadata = configurationManager.getConfigurationMetadata(configurationName);
      Iterator<Resource> configurationInheritanceChain = configurationResourceResolvingStrategy.getResourceInheritanceChain(resource, configurationBuckets, configurationName);

      while (configurationInheritanceChain != null && configurationInheritanceChain.hasNext()) {
        Resource configurationResource = configurationInheritanceChain.next();

        // get page for configuration resource - and all children (e.g. for config collections)
        Page configPage = pageManager.getContainingPage(configurationResource);
        if (configPage != null) {
          processReference(resource, configPage, configurationMetadata, references);
          Iterator<Page> deepChildren = configPage.listChildren(new PageFilter(false, true), true);
          while (deepChildren.hasNext()) {
            processReference(resource, deepChildren.next(), configurationMetadata, references);
          }
        }
      }
    }

    log.debug("Found {} references for resource {}", references.size(), resource.getPath());

    return references;
  }

  private void processReference(Resource resource, Page configPage, ConfigurationMetadata configurationMetadata,
      List<com.day.cq.wcm.api.reference.Reference> references) {
    log.trace("Found configuration reference {} for resource {}", configPage.getPath(), resource.getPath());
    references.add(new com.day.cq.wcm.api.reference.Reference(getType(),
        getReferenceName(configurationMetadata),
        configPage.adaptTo(Resource.class),
        getLastModifiedOf(configPage)));
  }

  private static String getReferenceName(ConfigurationMetadata configurationMetadata) {
    return StringUtils.defaultIfEmpty(configurationMetadata.getLabel(), configurationMetadata.getName());
  }

  private static long getLastModifiedOf(Page page) {
    Calendar lastModified = page.getLastModified();
    return lastModified != null ? lastModified.getTimeInMillis() : 0;
  }

  private static String getType() {
    return REFERENCE_TYPE;
  }

}
