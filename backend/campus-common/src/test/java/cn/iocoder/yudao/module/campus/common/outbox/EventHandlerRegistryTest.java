package cn.iocoder.yudao.module.campus.common.outbox;

import cn.iocoder.yudao.module.campus.common.event.CampusEventEnvelope;
import org.junit.jupiter.api.Test; import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EventHandlerRegistryTest {
    private final CampusEventEnvelope event=CampusEventEnvelope.builder().type("campus.test").version(2).build();
    @Test void routesSeveralNamedConsumersAndRejectsUnknownVersions(){EventHandlerRegistry registry=new EventHandlerRegistry(List.of(consumer("one"),consumer("two")));assertThat(registry.handlersFor(event)).extracting(OutboxConsumer::name).containsExactly("one","two");assertThat(registry.handlersFor(CampusEventEnvelope.builder().type("campus.test").version(3).build())).isEmpty();}
    @Test void duplicateNameForOneVersionIsInvalid(){assertThatThrownBy(()->new EventHandlerRegistry(List.of(consumer("one"),consumer("one")))).isInstanceOf(IllegalArgumentException.class);}
    private static OutboxConsumer consumer(String name){return new OutboxConsumer(){public String name(){return name;}public String eventType(){return "campus.test";}public int eventVersion(){return 2;}public ConsumerExecutionResult handle(CampusEventEnvelope e){return ConsumerExecutionResult.COMPLETED;}};}
}
