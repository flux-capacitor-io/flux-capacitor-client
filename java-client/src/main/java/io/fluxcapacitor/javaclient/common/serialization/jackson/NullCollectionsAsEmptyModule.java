package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

import static com.fasterxml.jackson.annotation.JsonSetter.Value.empty;

public class NullCollectionsAsEmptyModule extends SimpleModule {
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        JsonSetter.Value nullAsEmpty = empty().withValueNulls(Nulls.AS_EMPTY);
        context.configOverride(Collection.class).setSetterInfo(nullAsEmpty);
        context.configOverride(List.class).setSetterInfo(nullAsEmpty);
        context.configOverride(Set.class).setSetterInfo(nullAsEmpty);
        context.configOverride(SortedSet.class).setSetterInfo(nullAsEmpty);
        context.configOverride(Map.class).setSetterInfo(nullAsEmpty);
        context.configOverride(SortedMap.class).setSetterInfo(nullAsEmpty);
    }
}
