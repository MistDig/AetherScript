package dev.aetherscript.paper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.aetherscript.core.PersistentVariables;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class KitInventoryTest {
 @TempDir Path root;
 InventoryPlan.Item bread(int amount) {return new InventoryPlan.Item("minecraft:bread",amount,64);}
 InventoryPlan.Slot stack(int amount, boolean plain) {return new InventoryPlan.Slot("minecraft:bread",amount,64,plain);}
 @Test void failedBundlePreservesInputAndMetadata() {
  var source=new InventoryPlan.Slot[]{stack(63,true),stack(1,false)};
  assertNull(InventoryPlan.give(source,List.of(bread(2)))); assertEquals(63,source[0].amount());
  assertFalse(InventoryPlan.has(source,bread(64))); assertNull(InventoryPlan.remove(source,bread(64)));
  var next=InventoryPlan.remove(source,bread(63)); assertNull(next[0]); assertSame(source[1],next[1]);
 }
 @Test void bundlesRespectAggregateCapacityAndStackLimits() {
  var source=new InventoryPlan.Slot[]{stack(60,true),null};
  var result=InventoryPlan.give(source,List.of(bread(4),bread(64)));
  assertEquals(64,result[0].amount()); assertEquals(64,result[1].amount());
  assertNull(InventoryPlan.give(source,List.of(bread(4),bread(65))));
  assertNull(InventoryPlan.give(new InventoryPlan.Slot[1],List.of(new InventoryPlan.Item("minecraft:diamond_sword",2,1))));
 }
 @Test void cooldownSurvivesRestartAndBlocksDuplicates() throws Exception {
  UUID actor=UUID.randomUUID();
  try(var store=new KitCooldowns(root)) {
   assertNotNull(store.reserve("daily",actor,"daily",1000,86400));
   assertNull(store.reserve("daily",actor,"daily",1001,86400));
   assertNotNull(store.reserve("daily",UUID.randomUUID(),"daily",1001,86400));
  }
  try(var store=new KitCooldowns(root)) {
   assertNull(store.reserve("daily",actor,"daily",2000,86400));
   assertNotNull(store.reserve("daily",actor,"daily",86401000,86400));
  }
 }
 @Test void compensationCannotOverwriteLaterClaim() throws Exception {
  UUID actor=UUID.randomUUID();
  try(var store=new KitCooldowns(root)) {
   var first=store.reserve("daily",actor,"daily",1000,1); store.compensate(first);
   assertNotNull(store.reserve("daily",actor,"daily",1001,86400));
   assertThrows(PersistentVariables.ConflictException.class,()->store.compensate(first));
   assertNull(store.reserve("daily",actor,"daily",1002,86400));
  }
 }
 @Test void concurrentDuplicateRequestsHaveOneWinner() throws Exception {
  UUID actor=UUID.randomUUID();
  try(var store=new KitCooldowns(root);var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var a=executor.submit(()->store.reserve("daily",actor,"daily",1000,86400));
   var b=executor.submit(()->store.reserve("daily",actor,"daily",1000,86400));
   assertEquals(1, java.util.stream.Stream.of(a.get(),b.get()).filter(Objects::nonNull).count());
  }
 }
}
