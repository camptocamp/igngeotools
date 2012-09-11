/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2007-2011, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */

package org.geotools.data.complex.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.xml.namespace.QName;

import org.geotools.data.DataAccess;
import org.geotools.data.DataAccessFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.complex.AppSchemaDataAccess;
import org.geotools.data.complex.AttributeMapping;
import org.geotools.data.complex.FeatureTypeMapping;
import org.geotools.data.complex.TestData;
import org.geotools.data.complex.filter.XPath.Step;
import org.geotools.data.complex.filter.XPath.StepList;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.NameImpl;
import org.geotools.feature.TypeBuilder;
import org.geotools.feature.Types;
import org.geotools.feature.type.UniqueNameFeatureTypeFactoryImpl;
import org.geotools.filter.FilterFactoryImplNamespaceAware;
import org.geotools.filter.IsEqualsToImpl;
import org.geotools.filter.OrImpl;
import org.geotools.filter.spatial.BBOX3DImpl;
import org.geotools.geometry.jts.ReferencedEnvelope3D;
import org.geotools.gml3.GML;
import org.geotools.test.AppSchemaTestSupport;
import org.geotools.xlink.XLINK;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.FeatureTypeFactory;
import org.opengis.feature.type.Name;
import org.opengis.filter.And;
import org.opengis.filter.BinaryLogicOperator;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.Id;
import org.opengis.filter.MultiValuedFilter.MatchAction;
import org.opengis.filter.Or;
import org.opengis.filter.PropertyIsBetween;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.filter.PropertyIsGreaterThan;
import org.opengis.filter.PropertyIsLike;
import org.opengis.filter.PropertyIsNull;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.Function;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.Multiply;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Intersects;
import org.opengis.filter.temporal.After;
import org.opengis.filter.temporal.AnyInteracts;
import org.opengis.filter.temporal.Begins;
import org.opengis.filter.temporal.BegunBy;
import org.opengis.filter.temporal.During;
import org.opengis.filter.temporal.EndedBy;
import org.opengis.filter.temporal.Ends;
import org.opengis.filter.temporal.Meets;
import org.opengis.filter.temporal.MetBy;
import org.opengis.filter.temporal.OverlappedBy;
import org.opengis.filter.temporal.TContains;
import org.opengis.filter.temporal.TEquals;
import org.opengis.filter.temporal.TOverlaps;
import org.xml.sax.helpers.NamespaceSupport;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;

/**
 * 
 * @author Gabriel Roldan (Axios Engineering)
 * @version $Id$
 *
 *
 *
 * @source $URL$
 * @since 2.4
 */
public class UnmappingFilterVisitorTest extends AppSchemaTestSupport {

    private static FilterFactory2 ff = (FilterFactory2) CommonFactoryFinder.getFilterFactory(null);

    private UnmappingFilterVisitor visitor;

    MemoryDataStore dataStore;

    FeatureTypeMapping mapping;

    AttributeDescriptor targetDescriptor;

    FeatureType targetType;

    private static DataAccess<FeatureType, Feature> mappingDataStore;

    @Before
    public void setUp() throws Exception {
        dataStore = TestData.createDenormalizedWaterQualityResults();
        mapping = TestData.createMappingsGroupByStation(dataStore);
        visitor = new UnmappingFilterVisitor(mapping);
        targetDescriptor = mapping.getTargetFeature();
    }
    
    @BeforeClass
    public static void oneTimeSetUp() throws IOException {
        final String schemaBase = "/test-data/";
        final Map dsParams = new HashMap();
        final URL url = UnmappingFilterVisitorTest.class.getResource(schemaBase
                + "BoreholeTest_properties.xml");
        dsParams.put("dbtype", "app-schema");
        dsParams.put("url", url.toExternalForm());

        mappingDataStore = DataAccessFinder.getDataStore(dsParams);
    }

    /**
     * Creates a mapping from the test case simple source to a complex type with an
     * "areaOfInfluence" attribute, which is a buffer over the simple "location" attribute and
     * another which is the concatenation of the attributes "anzlic_no" and "project_no"
     * 
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private FeatureTypeMapping createSampleDerivedAttributeMappings() throws Exception {
        // create the target type
        FeatureTypeFactory tf = new UniqueNameFeatureTypeFactoryImpl();
        TypeBuilder builder = new TypeBuilder(tf);

        AttributeType areaOfInfluence = builder.name("areaOfInfluence").bind(Polygon.class)
                .attribute();
        AttributeType concatType = builder.name("concatenated").bind(String.class).attribute();

        builder.setName("target");
        builder.addAttribute("areaOfInfluence", areaOfInfluence);
        builder.addAttribute("concatenated", concatType);

        FeatureType targetType = builder.feature();
        AttributeDescriptor targetFeature = tf.createAttributeDescriptor(targetType, targetType
                .getName(), 0, Integer.MAX_VALUE, true, null);

        // create the mapping definition
        List attMappings = new LinkedList();

        NamespaceSupport namespaces = new NamespaceSupport();

        Function aoiExpr = ff.function("buffer", ff.property("location"), ff.literal(10));

        attMappings.add(new AttributeMapping(null, aoiExpr, XPath.steps(targetFeature,
                "areaOfInfluence", namespaces)));

        Function strConcat = ff.function("strConcat", ff.property("anzlic_no"), ff
                .property("project_no"));

        attMappings.add(new AttributeMapping(null, strConcat, XPath.steps(targetFeature,
                "concatenated", namespaces)));

        FeatureSource simpleSource = mapping.getSource();
        FeatureTypeMapping mapping = new FeatureTypeMapping(simpleSource, targetFeature,
                attMappings, namespaces);
        return mapping;
    }

    /**
     * Mapping specifies station_no --> wq_plus/@id. A FidFilter over wq_plus type should result in
     * a compare equals filter over the station_no attribute of wq_ir_results simple type.
     */
    @Test
    public void testUnrollFidMappedToAttribute() throws Exception {
        String fid = "station_no.1";
        Id fidFilter = ff.id(Collections.singleton(ff.featureId(fid)));

        Filter unrolled = (Filter) fidFilter.accept(visitor, null);
        assertNotNull(unrolled);

        FeatureCollection<SimpleFeatureType,SimpleFeature> results = mapping.getSource()
                .getFeatures(unrolled);
        assertEquals(1, getCount(results));

        Iterator<SimpleFeature> features = results.iterator();
        SimpleFeature unmappedFeature = (SimpleFeature) features.next();
        results.close(features);
        assertNotNull(unmappedFeature);
        Object object = unmappedFeature.getProperty("station_no").getValue();
        assertEquals(fid, object);
    }
    
    /**
     * The type of filter that returns should be in a single Or filter OR(a,b,c) instead 
     * of ((a OR b) or c)
     */
    @Test
    public void testUnrollFid() throws Exception {
        Set<FeatureId> fids = new HashSet();
        String fid1 = "station_no.1";// exists
        String fid2 = "station_no.500";// doesn't exists
        fids.add(ff.featureId(fid1));
        fids.add(ff.featureId(fid2));
        Id fidFilter = ff.id(fids);
        Filter unrolled = (Filter) fidFilter.accept(visitor, null);
        assertNotNull(unrolled);
        assertTrue(unrolled instanceof OrImpl);
        List<Filter> filters = ((OrImpl) unrolled).getChildren();
        assertEquals(2, filters.size());
        for (Filter f : filters) {
            assertTrue(f instanceof IsEqualsToImpl);
        }

        FeatureCollection<SimpleFeatureType, SimpleFeature> results = mapping.getSource()
                .getFeatures(unrolled);
        assertEquals(1, getCount(results));

        Iterator<SimpleFeature> features = results.iterator();
        SimpleFeature unmappedFeature = (SimpleFeature) features.next();
        results.close(features);
        assertNotNull(unmappedFeature);
        Object object = unmappedFeature.getProperty("station_no").getValue();
        assertEquals(fid1, object);
    }

    private int getCount(FeatureCollection features) {
        Iterator iterator = features.iterator();
        int count = 0;
        try {
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        } finally {
            features.close(iterator);
        }
        return count;
    }

    /**
     * If no a specific mapping is defined for the feature id, the same feature id from the
     * originating feature source is used. In such a case, an unrolled FidFilter should stay being a
     * FidFilter.
     */
    @Test
    public void testUnrollFidToFid() throws Exception {
        checkUnrollIdExpression(Expression.NIL);
    }

    /**
     * If a getId() idExpression is used for the for the mapping, the same feature id from the
     * originating feature source is used. In such a case, an unrolled FidFilter should stay being a
     * FidFilter.
     */
    @Test
    public void testUnrollGetidToGetid() throws Exception {
        checkUnrollIdExpression(CommonFactoryFinder.getFilterFactory(null).function("getID",
                new org.opengis.filter.expression.Expression[0]));
    }
    
    /**
     * Implementation for tests of fid -> fid unmapping.
     * 
     * @param idExpression
     * @throws Exception
     */
    private void checkUnrollIdExpression(Expression idExpression) throws Exception {
        AttributeMapping featureMapping = null;
        Name featurePath = mapping.getTargetFeature().getName();
        QName featureName = Types.toQName(featurePath);
        for (Iterator it = mapping.getAttributeMappings().iterator(); it.hasNext();) {
            AttributeMapping attMapping = (AttributeMapping) it.next();
            StepList targetXPath = attMapping.getTargetXPath();
            if (targetXPath.size() > 1) {
                continue;
            }
            Step step = (Step) targetXPath.get(0);
            if (featureName.equals(step.getName())) {
                featureMapping = attMapping;
                break;
            }
        }
        featureMapping.setIdentifierExpression(idExpression);
        this.visitor = new UnmappingFilterVisitor(this.mapping);
        FeatureCollection<SimpleFeatureType,SimpleFeature> content = mapping.getSource()
                .getFeatures();
        Iterator iterator = content.iterator();
        Feature sourceFeature = (Feature) iterator.next();
        content.close(iterator);
        String fid = sourceFeature.getIdentifier().toString();
        Id fidFilter = ff.id(Collections.singleton(ff.featureId(fid)));
        Filter unrolled = (Filter) fidFilter.accept(visitor, null);
        assertNotNull(unrolled);
        assertTrue(unrolled instanceof Id);

        FeatureCollection<SimpleFeatureType,SimpleFeature> results = mapping.getSource()
                .getFeatures(unrolled);
        assertEquals(1, getCount(results));
        iterator = results.iterator();
        SimpleFeature unmappedFeature = (SimpleFeature) iterator.next();
        results.close(iterator);
        assertEquals(fid, unmappedFeature.getID());
    }
    
    @Test
    public void testPropertyName() throws Exception {
        PropertyName ae = ff.property("/wq_plus/measurement/result");
        List unrolled = (List) ae.accept(visitor, null);
        assertNotNull(unrolled);
        assertEquals(1, unrolled.size());

        Expression unmappedExpr = (Expression) unrolled.get(0);
        assertTrue(unmappedExpr instanceof PropertyName);
        PropertyName attExp = (PropertyName) unmappedExpr;
        assertEquals("results_value", attExp.getPropertyName());

        // now try with an AttributeExpression that is not directly mapped to an
        // attribute
        // expresion on the surrogate FeatureType, but to a composite one.
        // For example, create a mapping from the test case simple source to
        // a complex type with an "areaOfInfluence" attribute, which is a buffer
        // over the simple "location" attribute
        // and another which is the concatenation of the attributes "anzlic_no"
        // and "project_no"
        FeatureTypeMapping mapping = createSampleDerivedAttributeMappings();
        targetDescriptor = mapping.getTargetFeature();

        visitor = new UnmappingFilterVisitor(mapping);
        attExp = ff.property("areaOfInfluence");
        List unrolledExpressions = (List) attExp.accept(visitor, null);

        assertNotNull(unrolledExpressions);
        assertEquals(1, unrolledExpressions.size());

        unmappedExpr = (Expression) unrolledExpressions.get(0);
        assertTrue(unmappedExpr instanceof Function);
        Function fe = (Function) unmappedExpr;
        assertEquals("buffer", fe.getName());

        Expression arg0 = (Expression) fe.getParameters().get(0);
        assertTrue(arg0 instanceof PropertyName);
        assertEquals("location", ((PropertyName) arg0).getPropertyName());
    }

    /**
     * An xpath expression may target a "client property" mapping (in xml land, an xml attribute
     * rather than a xml element).
     * 
     * @throws Exception
     */
    @Test
    public void testPropertyNameWithXlinkAttribute() throws Exception {
        final String XMMLNS = "http://www.opengis.net/xmml";
        final Name typeName = new NameImpl(XMMLNS, "Borehole");
        
        AppSchemaDataAccess complexDs = (AppSchemaDataAccess) mappingDataStore;
        
        mapping = complexDs.getMappingByElement(typeName);

        NamespaceSupport namespaces = new NamespaceSupport();
        namespaces.declarePrefix("gml", GML.NAMESPACE);
        namespaces.declarePrefix("xmml", XMMLNS);
        namespaces.declarePrefix("xlink", XLINK.NAMESPACE);

        FilterFactory2 ff = new FilterFactoryImplNamespaceAware(namespaces);
        String xpathExpression = "sa:shape/geo:LineByVector/geo:origin/@xlink:href";
        PropertyName propNameExpression = ff.property(xpathExpression);

        visitor = new UnmappingFilterVisitor(mapping);

        List /* <Expression> */unrolled = (List) propNameExpression.accept(visitor, null);
        assertNotNull(unrolled);
        assertEquals(1, unrolled.size());
        assertTrue(unrolled.get(0) instanceof Expression);
    }

    /**
     * An x-path expression may target a "client property" mapping (in xml land, an xml attribute
     * rather than a xml element).
     * 
     * @throws Exception
     */
    @Test
    public void testPropertyNameWithGmlIdAttribute() throws Exception {
        final String XMMLNS = "http://www.opengis.net/xmml";
        final Name typeName = new NameImpl(XMMLNS, "Borehole");

        AppSchemaDataAccess complexDs = (AppSchemaDataAccess) mappingDataStore;

        mapping = complexDs.getMappingByElement(typeName);

        NamespaceSupport namespaces = new NamespaceSupport();
        namespaces.declarePrefix("gml", GML.NAMESPACE);
        namespaces.declarePrefix("xmml", XMMLNS);
        namespaces.declarePrefix("xlink", XLINK.NAMESPACE);

        visitor = new UnmappingFilterVisitor(mapping);
        FilterFactory2 ff = new FilterFactoryImplNamespaceAware(namespaces);

        String xpathExpression = "@gml:id";
        PropertyName propNameExpression = ff.property(xpathExpression);

        List /* <Expression> */unrolled = (List) propNameExpression.accept(visitor, null);
        assertNotNull(unrolled);
        assertEquals(1, unrolled.size());
        assertTrue(unrolled.get(0) instanceof Expression);
        assertEquals(((Expression) unrolled.get(0)).toString(), "strConcat([bh.], [BGS_ID])");

        xpathExpression = "/@gml:id";
        propNameExpression = ff.property(xpathExpression);

        unrolled = (List) propNameExpression.accept(visitor, null);
        assertNotNull(unrolled);
        assertEquals(1, unrolled.size());
        assertTrue(unrolled.get(0) instanceof Expression);
        assertEquals(((Expression) unrolled.get(0)).toString(), "strConcat([bh.], [BGS_ID])");

        xpathExpression = "xmml:Borehole/@gml:id";
        propNameExpression = ff.property(xpathExpression);

        unrolled = (List) propNameExpression.accept(visitor, null);
        assertNotNull(unrolled);
        assertEquals(1, unrolled.size());
        assertTrue(unrolled.get(0) instanceof Expression);
        assertEquals(((Expression) unrolled.get(0)).toString(), "strConcat([bh.], [BGS_ID])");
    }

    @Test
    public void testBetweenFilter() throws Exception {
        PropertyIsBetween bf = ff.between(ff.property("measurement/result"), ff.literal(1), ff
                .literal(2), MatchAction.ALL);

        PropertyIsBetween unrolled = (PropertyIsBetween) bf.accept(visitor, null);
        assertEquals(MatchAction.ALL, ((PropertyIsBetween) unrolled).getMatchAction());
        Expression att = unrolled.getExpression();
        assertTrue(att instanceof PropertyName);
        String propertyName = ((PropertyName) att).getPropertyName();
        assertEquals("results_value", propertyName);
    }       

    @Test
    public void testCompareFilter() throws Exception {
        PropertyIsEqualTo complexFilter = ff.equal(ff.property("measurement/result"), ff
                .literal(1.1), true, MatchAction.ALL);

        Filter unrolled = (Filter) complexFilter.accept(visitor, null);
        assertNotNull(unrolled);
        assertTrue(unrolled instanceof PropertyIsEqualTo);
        assertNotSame(complexFilter, unrolled);
        assertTrue(((PropertyIsEqualTo) unrolled).isMatchingCase());
        assertEquals(MatchAction.ALL, ((PropertyIsEqualTo) unrolled).getMatchAction());

        Expression left = ((PropertyIsEqualTo) unrolled).getExpression1();
        Expression right = ((PropertyIsEqualTo) unrolled).getExpression2();

        assertTrue(left instanceof PropertyName);
        assertTrue(right instanceof Literal);
        PropertyName attExp = (PropertyName) left;
        String expectedAtt = "results_value";
        assertEquals(expectedAtt, attExp.getPropertyName());
        assertEquals(new Double(1.1), ((Literal) right).getValue());
    }

    /**
     * There might be multiple mappings per propery name, like <code>gml:name[1] = att1</code>,
     * <code>gml:name2 = strConcat(att2, att3)</code>, <code>gml:name[3] = "sampleValue</code>.
     * <p>
     * In the BoreHole test mapping used here, the following mappings exist for gml:name:
     * <ul>
     * <li>gml:name[1] = strConcat( strConcat(QS, strConcat("/", RT)), strConcat(strConcat("/",
     * NUMB), strConcat("/", BSUFF)) )</li>
     * <li>gml:name[2] = BGS_ID</li>
     * <li>gml:name[3] = NAME</li>
     * <li>gml:name[4] = ORIGINAL_N</li>
     * </ul>
     * 
     * This means the "unrolled" filter for <code>gml:name = "SWADLINCOTE"</code> should be
     * <code>strConcat( strConcat(QS, ...) = "SWADLINCOTE" 
     *          OR BGS_ID = "SWADLINCOTE" 
     *          OR NAME = "SWADLINCOTE" 
     *          OR ORIGINAL_N = "SWADLINCOTE"</code>
     * <p>
     * 
     * @throws Exception
     */
    @Test
    public void testCompareFilterMultipleMappingsPerPropertyName() throws Exception {
        final String XMMLNS = "http://www.opengis.net/xmml";
        final Name typeName = new NameImpl(XMMLNS, "Borehole");
        
        AppSchemaDataAccess complexDs = (AppSchemaDataAccess) mappingDataStore;
        mapping = complexDs.getMappingByElement(typeName);

        NamespaceSupport namespaces = new NamespaceSupport();
        namespaces.declarePrefix("gml", GML.NAMESPACE);
        namespaces.declarePrefix("xmml", XMMLNS);

        FilterFactory2 ff = new FilterFactoryImplNamespaceAware(namespaces);
        PropertyIsEqualTo complexFilter = ff.equals(ff.property("gml:name"), ff
                .literal("SWADLINCOTE"));

        visitor = new UnmappingFilterVisitor(mapping);

        Filter unrolled = (Filter) complexFilter.accept(visitor, null);
        assertNotNull(unrolled);
        assertNotSame(complexFilter, unrolled);

        assertTrue(unrolled.getClass().getName(), unrolled instanceof org.opengis.filter.Or);

        Or oredFilter = (Or) unrolled;
        List children = oredFilter.getChildren();
        assertEquals(4, children.size());

        assertTrue(children.get(0) instanceof PropertyIsEqualTo);
        assertTrue(children.get(1) instanceof PropertyIsEqualTo);
        assertTrue(children.get(2) instanceof PropertyIsEqualTo);
        assertTrue(children.get(3) instanceof PropertyIsEqualTo);

        PropertyIsEqualTo filter1 = (PropertyIsEqualTo) children.get(0);
        PropertyIsEqualTo filter2 = (PropertyIsEqualTo) children.get(1);
        PropertyIsEqualTo filter3 = (PropertyIsEqualTo) children.get(2);
        PropertyIsEqualTo filter4 = (PropertyIsEqualTo) children.get(3);

        assertTrue(filter1.getExpression1() instanceof Function);
        assertTrue(filter2.getExpression1() instanceof PropertyName);
        assertTrue(filter3.getExpression1() instanceof PropertyName);
        assertTrue(filter4.getExpression1() instanceof PropertyName);

        assertTrue(filter1.getExpression2() instanceof Literal);
        assertTrue(filter2.getExpression2() instanceof Literal);
        assertTrue(filter3.getExpression2() instanceof Literal);
        assertTrue(filter4.getExpression2() instanceof Literal);

        assertEquals("BGS_ID", ((PropertyName) filter2.getExpression1()).getPropertyName());
        assertEquals("NAME", ((PropertyName) filter3.getExpression1()).getPropertyName());
        assertEquals("ORIGINAL_N", ((PropertyName) filter4.getExpression1()).getPropertyName());        
    }

    @Test
    public void testLogicFilterAnd() throws Exception {
        PropertyIsEqualTo equals = ff.equals(ff.property("measurement/result"), ff.literal(1.1));
        PropertyIsGreaterThan greater = ff.greater(ff
                .property("measurement/determinand_description"), ff.literal("desc1"));

        And logicFilter = ff.and(equals, greater);

        Filter unrolled = (Filter) logicFilter.accept(visitor, null);
        assertNotNull(unrolled);
        assertTrue(unrolled instanceof And);
        assertNotSame(equals, unrolled);

        And sourceAnd = (And) unrolled;
        assertEquals(2, sourceAnd.getChildren().size());

        Filter sourceEquals = (Filter) sourceAnd.getChildren().get(0);
        assertTrue(sourceEquals instanceof PropertyIsEqualTo);

        Expression left = ((PropertyIsEqualTo) sourceEquals).getExpression1();
        Expression right = ((PropertyIsEqualTo) sourceEquals).getExpression2();
        assertTrue(left instanceof PropertyName);
        assertTrue(right instanceof Literal);

        assertEquals("results_value", ((PropertyName) left).getPropertyName());
        assertEquals(new Double(1.1), ((Literal) right).getValue());

        Filter sourceGreater = (Filter) sourceAnd.getChildren().get(1);
        assertTrue(sourceGreater instanceof PropertyIsGreaterThan);

        left = ((PropertyIsGreaterThan) sourceGreater).getExpression1();
        right = ((PropertyIsGreaterThan) sourceGreater).getExpression2();
        assertTrue(left instanceof PropertyName);
        assertTrue(right instanceof Literal);

        assertEquals("determinand_description", ((PropertyName) left).getPropertyName());
        assertEquals("desc1", ((Literal) right).getValue());
    }

    @Test
    public void testFunction() throws Exception {
        Function fe = ff.function("strIndexOf",
                ff.property("/measurement/determinand_description"), ff
                        .literal("determinand_description_1"));

        List unrolledExpressions = (List) fe.accept(visitor, null);

        Expression unmapped = (Expression) unrolledExpressions.get(0);
        assertTrue(unmapped instanceof Function);
        List params = ((Function) unmapped).getParameters();
        assertEquals(2, params.size());
        assertTrue(params.get(0) instanceof PropertyName);
        assertEquals("determinand_description", ((PropertyName) params.get(0)).getPropertyName());
    }

    @Test
    public void testGeometryFilter() throws Exception {
        mapping = createSampleDerivedAttributeMappings();
        visitor = new UnmappingFilterVisitor(mapping);
        targetDescriptor = mapping.getTargetFeature();
        targetType = (FeatureType) targetDescriptor.getType();

        Expression literalGeom = ff
                .literal(new GeometryFactory().createPoint(new Coordinate(1, 1)));

        Intersects gf = ff.intersects(ff.property("areaOfInfluence"), literalGeom, MatchAction.ALL);

        Filter unrolled = (Filter) gf.accept(visitor, null);
        assertTrue(unrolled instanceof Intersects);
        assertNotSame(gf, unrolled);
        assertEquals(MatchAction.ALL, ((Intersects) unrolled).getMatchAction());

        Intersects newFilter = (Intersects) unrolled;
        Expression left = newFilter.getExpression1();
        Expression right = newFilter.getExpression2();

        assertSame(right, literalGeom);
        assertTrue(left instanceof Function);
        Function fe = (Function) left;
        assertEquals("buffer", fe.getName());

        Expression arg0 = (Expression) fe.getParameters().get(0);
        assertTrue(arg0 instanceof PropertyName);
        assertEquals("location", ((PropertyName) arg0).getPropertyName());
    }

    @Test
    public void testLikeFilter() throws Exception {
        final String wildcard = "%";
        final String single = "?";
        final String escape = "\\";
        PropertyIsLike like = ff.like(ff.property("/measurement/determinand_description"),
                "%n_1_1", wildcard, single, escape, true, MatchAction.ONE);

        PropertyIsLike unmapped = (PropertyIsLike) like.accept(visitor, null);
        assertEquals(like.getLiteral(), unmapped.getLiteral());
        assertEquals(like.getWildCard(), unmapped.getWildCard());
        assertEquals(like.getSingleChar(), unmapped.getSingleChar());
        assertEquals(like.getEscape(), unmapped.getEscape());
        assertEquals(like.isMatchingCase(), unmapped.isMatchingCase());
        assertEquals(like.getMatchAction(), unmapped.getMatchAction());

        Expression unmappedExpr = unmapped.getExpression();
        assertTrue(unmappedExpr instanceof PropertyName);
        assertEquals("determinand_description", ((PropertyName) unmappedExpr).getPropertyName());
    }
    
    @Test
    public void testLiteralExpression() throws Exception {
        Expression literal = ff.literal(new Integer(0));
        List unrolledExpressions = (List) literal.accept(visitor, null);
        assertEquals(1, unrolledExpressions.size());
        assertSame(literal, unrolledExpressions.get(0));
    }

    @Test
    public void testLogicFilter() throws Exception {
        testLogicFilter(And.class);
        testLogicFilter(Or.class);
    }

    private void testLogicFilter(Class filterType) throws Exception {
        BinaryLogicOperator complexLogicFilter;
        PropertyIsGreaterThan resultFilter = ff.greater(ff.property("measurement/result"), ff
                .literal(new Integer(5)));

        PropertyIsBetween determFilter = ff.between(ff
                .property("measurement/determinand_description"), ff
                .literal("determinand_description_1_1"), ff.literal("determinand_description_3_3"));

        if (And.class.equals(filterType)) {
            complexLogicFilter = ff.and(resultFilter, determFilter);
        } else if (Or.class.equals(filterType)) {
            complexLogicFilter = ff.or(resultFilter, determFilter);
        } else {
            throw new IllegalArgumentException();
        }

        Filter unmapped = (Filter) complexLogicFilter.accept(visitor, null);
        assertNotNull(unmapped);
        assertTrue(unmapped instanceof BinaryLogicOperator);
        assertNotSame(complexLogicFilter, unmapped);

        BinaryLogicOperator logicUnmapped = (BinaryLogicOperator) unmapped;

        List children = logicUnmapped.getChildren();
        assertEquals(2, children.size());

        PropertyIsGreaterThan unmappedResult = (PropertyIsGreaterThan) children.get(0);
        PropertyIsBetween unmappedDeterm = (PropertyIsBetween) children.get(1);

        assertEquals("results_value", ((PropertyName) unmappedResult.getExpression1())
                .getPropertyName());

        assertEquals(new Integer(5), ((Literal) unmappedResult.getExpression2()).getValue());

        assertEquals("determinand_description", ((PropertyName) unmappedDeterm.getExpression())
                .getPropertyName());
        assertEquals("determinand_description_1_1", ((Literal) unmappedDeterm.getLowerBoundary())
                .getValue());
        assertEquals("determinand_description_3_3", ((Literal) unmappedDeterm.getUpperBoundary())
                .getValue());
    }

    @Test
    public void testMathExpression() throws Exception {
        Literal literal = ff.literal(new Integer(2));
        Multiply mathExp = ff.multiply(ff.property("measurement/result"), literal);

        List unrolledExpressions = (List) mathExp.accept(visitor, null);

        assertEquals(1, unrolledExpressions.size());
        Expression unmapped = (Expression) unrolledExpressions.get(0);
        assertTrue(unmapped instanceof Multiply);
        Multiply mathUnmapped = (Multiply) unmapped;

        PropertyName unmappedAttt = (PropertyName) mathUnmapped.getExpression1();
        assertEquals("results_value", unmappedAttt.getPropertyName());
        assertSame(literal, mathUnmapped.getExpression2());
    }

    @Test
    public void testNullFilter() throws Exception {
        PropertyIsNull nullFilter = ff.isNull(ff.property("measurement/result"));

        Filter unrolled = (Filter) nullFilter.accept(visitor, null);
        assertTrue(unrolled instanceof PropertyIsNull);
        assertNotSame(nullFilter, unrolled);

        PropertyIsNull unmapped = (PropertyIsNull) unrolled;
        Expression unmappedAtt = unmapped.getExpression();
        assertTrue(unmappedAtt instanceof PropertyName);
        assertEquals("results_value", ((PropertyName) unmappedAtt).getPropertyName());
    }
    
    @Test
    public void testBBox3D() throws Exception {
    	BBOX bbox = ff.bbox("location", new ReferencedEnvelope3D(0, 10, 20, 50, 60, 70, null));
    	
    	assertTrue(bbox instanceof BBOX3DImpl);
    	BBOX3DImpl bbox3d = (BBOX3DImpl) bbox;
    	
    	Filter unrolled = (Filter) bbox.accept(visitor, null);
    	
    	assertTrue(unrolled instanceof BBOX3DImpl);
    	BBOX3DImpl unrolled3d = (BBOX3DImpl) unrolled;
    	assertEquals(bbox3d.getMinX(), unrolled3d.getMinX(), 0.0);
    	assertEquals(bbox3d.getMaxX(), unrolled3d.getMaxX(), 0.0);
    	assertEquals(bbox3d.getMinY(), unrolled3d.getMinY(), 0.0);
    	assertEquals(bbox3d.getMaxY(), unrolled3d.getMaxY(), 0.0);
    	assertEquals(bbox3d.getMinZ(), unrolled3d.getMinZ(), 0.0);
    	assertEquals(bbox3d.getMaxZ(), unrolled3d.getMaxZ(), 0.0);
    	
    }

    // Tests for temporal filters
	@Test
	public void testAfterFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		After after = ff.after(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		After unmapped = (After) after.accept(visitor, null);
		assertEquals(after.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(after.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testAnyInteractsFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		AnyInteracts after = ff.anyInteracts(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		AnyInteracts unmapped = (AnyInteracts) after.accept(visitor, null);
		assertEquals(after.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(after.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testBeforeFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		org.opengis.filter.temporal.Before filter = ff.before(
				ff.property("last_maintenance"), ff.literal(cal.getTime()));

		org.opengis.filter.temporal.Before unmapped = (org.opengis.filter.temporal.Before) filter
				.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testBeginsFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		Begins filter = ff.begins(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		Begins unmapped = (Begins) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testBegunByFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		BegunBy filter = ff.begunBy(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		BegunBy unmapped = (BegunBy) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testDuringFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		During filter = ff.during(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		During unmapped = (During) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testEndedByFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		EndedBy filter = ff.endedBy(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		EndedBy unmapped = (EndedBy) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testEndsFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		Ends filter = ff.ends(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		Ends unmapped = (Ends) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testMeetsFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		Meets filter = ff.meets(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		Meets unmapped = (Meets) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testMetByFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		MetBy filter = ff.metBy(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		MetBy unmapped = (MetBy) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testOverlappedByFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		OverlappedBy filter = ff.overlappedBy(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		OverlappedBy unmapped = (OverlappedBy) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testTContainsFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		TContains filter = ff.tcontains(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		TContains unmapped = (TContains) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testTEqualsFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		TEquals filter = ff.tequals(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		TEquals unmapped = (TEquals) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

	@Test
	public void testTOverlapsFilter() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		cal.set(1905, 0, 1);
		TOverlaps filter = ff.toverlaps(ff.property("last_maintenance"),
				ff.literal(cal.getTime()));

		TOverlaps unmapped = (TOverlaps) filter.accept(visitor, null);
		assertEquals(filter.getMatchAction(), unmapped.getMatchAction());

		Expression unmappedExpr1 = unmapped.getExpression1();
		assertTrue(unmappedExpr1 instanceof PropertyName);
		assertEquals("maintenance",
				((PropertyName) unmappedExpr1).getPropertyName());
		assertEquals(filter.getExpression2(), unmapped.getExpression2());
	}

}
