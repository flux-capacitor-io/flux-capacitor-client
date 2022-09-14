package io.fluxcapacitor.javaclient.modeling

import io.fluxcapacitor.javaclient.modeling.AggregateEntitiesTest.MissingChild
import io.fluxcapacitor.javaclient.tracking.handling.IllegalCommandException

data class KotlinUpdateCommandThatFailsIfChildDoesNotExist (val missingChildId: String) {
    @AssertLegal
    fun assertLegal(child: MissingChild?) {
        if (child == null) {
            throw IllegalCommandException("Expected a child")
        }
    }
}