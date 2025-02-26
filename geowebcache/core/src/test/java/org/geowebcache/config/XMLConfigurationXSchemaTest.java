/**
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * @author Kevin Smith - Boundless (2015)
 */
package org.geowebcache.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThrows;

import com.thoughtworks.xstream.XStream;
import java.util.Collections;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.util.PropertyRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;

public class XMLConfigurationXSchemaTest {

    @Rule
    public PropertyRule whitelistProperty = PropertyRule.system("GEOWEBCACHE_XSTREAM_WHITELIST");

    @Test
    public void testNotAllowNonGWCClass() throws Exception {
        // Check that classes from other packages on the class path can't be serialized
        ContextualConfigurationProvider.Context pc = ContextualConfigurationProvider.Context.REST;
        try (StaticWebApplicationContext wac = new StaticWebApplicationContext()) {
            XStream xs = XMLConfiguration.getConfiguredXStreamWithContext(new GeoWebCacheXStream(), wac, pc);
            assertThrows(
                    com.thoughtworks.xstream.security.ForbiddenClassException.class,
                    () -> xs.fromXML("<" + org.easymock.Capture.class.getCanonicalName() + " />"));
        }
    }

    @Ignore // Need to tighten the XStream permissions to get this to pass
    @Test
    public void testNotAllowNonXMLGWCClass() throws Exception {
        // Check that a class in GWC that shouldn't be serialized to XML can't be
        ContextualConfigurationProvider.Context pc = ContextualConfigurationProvider.Context.REST;
        try (StaticWebApplicationContext wac = new StaticWebApplicationContext()) {
            XStream xs = XMLConfiguration.getConfiguredXStreamWithContext(new GeoWebCacheXStream(), wac, pc);
            assertThrows(
                    com.thoughtworks.xstream.security.ForbiddenClassException.class,
                    () -> xs.fromXML("<" + XMLConfigurationXSchemaTest.class.getCanonicalName() + " />"));
        }
    }

    @Test
    public void testExtensionsCanAllow() throws Exception {
        // Check that an XMLConfigurationProvider can add a class to the whitelist

        XStream xs = new GeoWebCacheXStream();

        ContextualConfigurationProvider.Context pc = ContextualConfigurationProvider.Context.REST;
        WebApplicationContext wac = EasyMock.createMock("wac", WebApplicationContext.class);
        XMLConfigurationProvider provider = EasyMock.createMock("provider", XMLConfigurationProvider.class);
        EasyMock.expect(wac.getBeansOfType(XMLConfigurationProvider.class))
                .andReturn(Collections.singletonMap("provider", provider));
        EasyMock.expect(wac.getBean("provider")).andReturn(provider);
        final Capture<XStream> xsCap = EasyMock.newCapture();
        EasyMock.expect(provider.getConfiguredXStream(EasyMock.capture(xsCap))).andStubAnswer(() -> {
            XStream xs1 = xsCap.getValue();
            xs1.allowTypes(new Class[] {Capture.class});
            return xs1;
        });

        EasyMock.replay(wac, provider);

        xs = XMLConfiguration.getConfiguredXStreamWithContext(xs, wac, pc);

        Object o = xs.fromXML("<" + org.easymock.Capture.class.getCanonicalName() + " />");

        assertThat(o, instanceOf(org.easymock.Capture.class));

        EasyMock.verify(wac, provider);
    }

    @Test
    public void testPropertyCanAllow() throws Exception {
        // Check that additional whitelist entries can be added via a system property.

        whitelistProperty.setValue("org.easymock.**");

        ContextualConfigurationProvider.Context pc = ContextualConfigurationProvider.Context.REST;
        try (StaticWebApplicationContext wac = new StaticWebApplicationContext()) {
            XStream xs = new GeoWebCacheXStream();

            xs = XMLConfiguration.getConfiguredXStreamWithContext(xs, wac, pc);

            Object o = xs.fromXML("<" + org.easymock.Capture.class.getCanonicalName() + " />");

            assertThat(o, instanceOf(org.easymock.Capture.class));
        }
    }

    @Test
    public void testPropertyCanAllowMultiple() throws Exception {
        // Check that additional whitelist entries can be added via a system property.

        whitelistProperty.setValue("org.easymock.**; org.junit.**");

        ContextualConfigurationProvider.Context pc = ContextualConfigurationProvider.Context.REST;
        try (StaticWebApplicationContext wac = new StaticWebApplicationContext()) {
            XStream xs = new GeoWebCacheXStream();

            xs = XMLConfiguration.getConfiguredXStreamWithContext(xs, wac, pc);

            Object o1 = xs.fromXML("<" + org.easymock.Capture.class.getCanonicalName() + " />");
            Object o2 = xs.fromXML("<" + org.junit.rules.TestName.class.getCanonicalName() + " />");

            assertThat(o1, instanceOf(org.easymock.Capture.class));
            assertThat(o2, instanceOf(org.junit.rules.TestName.class));
        }
    }
}
