package com.globo.galeb.test.unit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.*;

import org.junit.Test;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import com.globo.galeb.core.Farm;
import com.globo.galeb.core.VirtualServer;
import com.globo.galeb.core.bus.IQueueService;
import com.globo.galeb.core.entity.IJsonable;
import com.globo.galeb.metrics.ICounter;
import com.globo.galeb.rules.Rule;

public class VirtualServerTest {

    VirtualServer virtualserver = new VirtualServer();

    @Test
    public void createWithDefaultContructor() {
        assertThat(String.format("%s", virtualserver)).isEqualTo("UNDEF");
    }

    @Test
    public void createWithContructorWithId() {
        String id = "NOT_UNDEF";
        VirtualServer virtualserver2 = new VirtualServer(id);
        assertThat(String.format("%s", virtualserver2)).isEqualTo(id);
    }

    @Test
    public void createWithContructorWithJson() {
        String id = "NOT_UNDEF";
        JsonObject json = new JsonObject();
        json.putString(IJsonable.ID_FIELDNAME, id);
        VirtualServer virtualserver2 = new VirtualServer(json);

        assertThat(String.format("%s", virtualserver2)).isEqualTo(id);
    }

    @Test
    public void areEqualsIfIdIsTheSame() {
        String sameId = "sameId";
        VirtualServer virtualserver1 = new VirtualServer(sameId);
        VirtualServer virtualserver2 = new VirtualServer(sameId);
        assertThat(virtualserver1).isEqualTo(virtualserver2);
    }

    @Test
    public void areEqualsIfIdIsNotDefined() {
        VirtualServer virtualserver1 = new VirtualServer();
        VirtualServer virtualserver2 = new VirtualServer();
        assertThat(virtualserver1).isEqualTo(virtualserver2);
    }

    @Test
    public void areNotEqualsIfIdIsDifferent() {
        String oneId = "oneId";
        String otherId = "otherId";

        VirtualServer virtualserver1 = new VirtualServer(oneId);
        VirtualServer virtualserver2 = new VirtualServer(otherId);
        assertThat(virtualserver1).isNotEqualTo(virtualserver2);
    }

    private boolean createRule(String ruleId, final VirtualServer virtualserver) {
        Rule rule = mock(Rule.class);

        when(rule.setLogger((Logger) anyObject())).thenReturn(rule);
        when(rule.setCounter((ICounter) anyObject())).thenReturn(rule);
        when(rule.setQueueService((IQueueService) anyObject())).thenReturn(rule);
        when(rule.setFarm((Farm) anyObject())).thenReturn(rule);
        when(rule.setPlataform(anyObject())).thenReturn(rule);
        when(rule.setStaticConf((String) anyObject())).thenReturn(rule);
        when(rule.setStaticConf((JsonObject) anyObject())).thenReturn(rule);

        return virtualserver.addEntity(rule);
    }

    @Test
    public void addNewRule() {
        String ruleId = "newrule";
        boolean ruleCreated = createRule(ruleId, virtualserver);

        assertThat(ruleCreated).isTrue();
        assertThat(virtualserver.getNumEntities()).isEqualTo(1);
    }

    @Test
    public void addExistingRule() {
        String ruleId = "newrule";
        createRule(ruleId, virtualserver);
        boolean ruleCreated = createRule(ruleId, virtualserver);

        assertThat(ruleCreated).isFalse();
        assertThat(virtualserver.getNumEntities()).isEqualTo(1);
    }

    private boolean removeRule(String ruleId, final VirtualServer virtualserver) {
        Rule rule = mock(Rule.class);

        when(rule.setLogger((Logger) anyObject())).thenReturn(rule);
        when(rule.setCounter((ICounter) anyObject())).thenReturn(rule);
        when(rule.setQueueService((IQueueService) anyObject())).thenReturn(rule);
        when(rule.setFarm((Farm) anyObject())).thenReturn(rule);
        when(rule.setPlataform(anyObject())).thenReturn(rule);
        when(rule.setStaticConf((String) anyObject())).thenReturn(rule);
        when(rule.setStaticConf((JsonObject) anyObject())).thenReturn(rule);

        return virtualserver.removeEntity(rule);
    }

    @Test
    public void removeExistingRule() {
        String ruleId = "myrule";
        createRule(ruleId, virtualserver);
        boolean ruleRemoved = removeRule(ruleId, virtualserver);

        assertThat(ruleRemoved).isTrue();
        assertThat(virtualserver.getNumEntities()).isEqualTo(0);
    }

    @Test
    public void removeAbsentRule() {
        String ruleId = "myrule";
        boolean ruleRemoved = removeRule(ruleId, virtualserver);

        assertThat(ruleRemoved).isFalse();
        assertThat(virtualserver.getNumEntities()).isEqualTo(0);
    }
}