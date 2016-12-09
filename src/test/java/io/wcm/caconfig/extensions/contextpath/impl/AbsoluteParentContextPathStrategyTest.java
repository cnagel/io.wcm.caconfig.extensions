/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2016 wcm.io
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
package io.wcm.caconfig.extensions.contextpath.impl;

import static io.wcm.caconfig.extensions.contextpath.impl.TestUtils.assertNoResult;
import static io.wcm.caconfig.extensions.contextpath.impl.TestUtils.assertResult;
import static org.mockito.Mockito.when;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.caconfig.resource.spi.ContextPathStrategy;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.wcm.caconfig.application.ApplicationFinder;
import io.wcm.caconfig.application.ApplicationInfo;
import io.wcm.testing.mock.aem.junit.AemContext;

@RunWith(MockitoJUnitRunner.class)
public class AbsoluteParentContextPathStrategyTest {

  private static final String APP_ID = "/apps/app1";

  @Rule
  public AemContext context = new AemContext();

  @Mock
  private ApplicationFinder applicationFinder;

  private Resource level1;
  private Resource level2;
  private Resource level3;
  private Resource level4;

  @Before
  public void setUp() {
    context.registerService(ApplicationFinder.class, applicationFinder);

    level1 = context.create().page("/content/region1").adaptTo(Resource.class);
    level2 = context.create().page("/content/region1/site1").adaptTo(Resource.class);
    level3 = context.create().page("/content/region1/site1/en").adaptTo(Resource.class);
    level4 = context.create().page("/content/region1/site1/en/page1").adaptTo(Resource.class);
  }

  @Test
  public void testWithInvalidConfig() {
    ContextPathStrategy underTest = context.registerInjectActivateService(new AbsoluteParentContextPathStrategy());

    assertNoResult(underTest.findContextResources(level4));
  }

  @Test
  public void testWithLevels() {
    ContextPathStrategy underTest = context.registerInjectActivateService(new AbsoluteParentContextPathStrategy(),
        "levels", new int[] { 1, 3 });

    assertResult(underTest.findContextResources(level4),
        "/content/region1/site1/en", "/conf/content/region1/site1/en",
        "/content/region1", "/conf/content/region1");

    assertResult(underTest.findContextResources(level3),
        "/content/region1/site1/en", "/conf/content/region1/site1/en",
        "/content/region1", "/conf/content/region1");

    assertResult(underTest.findContextResources(level2),
        "/content/region1", "/conf/content/region1");

    assertResult(underTest.findContextResources(level1),
        "/content/region1", "/conf/content/region1");
  }

  @Test
  public void testWithNoMatchingApplication() {
    ContextPathStrategy underTest = context.registerInjectActivateService(new AbsoluteParentContextPathStrategy(),
        "levels", new int[] { 1, 3 },
        "applicationId", APP_ID);

    assertNoResult(underTest.findContextResources(level4));
  }

  @Test
  public void testWithMatchingApplication() {
    ContextPathStrategy underTest = context.registerInjectActivateService(new AbsoluteParentContextPathStrategy(),
        "levels", new int[] { 1, 3 },
        "applicationId", APP_ID);
    when(applicationFinder.find(level4)).thenReturn(new ApplicationInfo(APP_ID));

    assertResult(underTest.findContextResources(level4),
        "/content/region1/site1/en", "/conf/content/region1/site1/en",
        "/content/region1", "/conf/content/region1");
  }

  @Test
  public void testWithAlternativePatterns() {
    ContextPathStrategy underTest = context.registerInjectActivateService(new AbsoluteParentContextPathStrategy(),
        "levels", new int[] { 1, 3 },
        "contextPathRegex", "^/content(/.+)$",
        "configPathPattern", "/conf/test$1");

    assertResult(underTest.findContextResources(level4),
        "/content/region1/site1/en", "/conf/test/region1/site1/en",
        "/content/region1", "/conf/test/region1");
  }

}
