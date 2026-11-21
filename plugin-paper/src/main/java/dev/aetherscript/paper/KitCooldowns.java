package dev.aetherscript.paper;
import dev.aetherscript.core.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
/** Worker-only reservations; independent database prevents state.save from altering cooldowns. */
final class KitCooldowns implements AutoCloseable {
 private final SQLiteVariables storage;
 record Reservation(PersistentVariables.Key key,long revision,long previous) {}
 KitCooldowns(Path directory) throws IOException { storage=new SQLiteVariables(directory.resolve("kit-cooldowns")); }
 synchronized Reservation reserve(String feature,UUID actor,String name,long now,long seconds) throws IOException {
  var key=new PersistentVariables.Key(feature,actor,name); var old=storage.get(key);
  long previous=old==null?0:old.value().integer();
  if(previous>now) return null;
  long revision=old==null?0:old.revision();
  storage.commit(List.of(new PersistentVariables.Mutation(key,revision,PersistentVariables.Value.integer(Math.addExact(now,Math.multiplyExact(seconds,1000))))));
  return new Reservation(key,revision+1,previous);
 }
 synchronized void compensate(Reservation reservation) throws IOException {
  storage.commit(List.of(new PersistentVariables.Mutation(reservation.key(),reservation.revision(),PersistentVariables.Value.integer(reservation.previous()))));
 }
 @Override public void close() throws IOException {storage.close();}
}
