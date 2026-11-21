package dev.aetherscript.paper;
import java.util.*;
/** Pure bounded inventory planning: a failed plan never changes the input. */
final class InventoryPlan {
 record Slot(String material, int amount, int maximum, boolean plain) {}
 record Item(String material, int amount, int maximum) {}
 static Slot[] give(Slot[] source, List<Item> items) {
  Slot[] next = source.clone();
  for (Item item : items) {
   int left = item.amount();
   for (int i=0;i<next.length && left>0;i++) {
    Slot slot=next[i];
    if(slot!=null && slot.plain() && slot.material().equals(item.material())) {
     int add=Math.min(left,Math.max(0,Math.min(slot.maximum(),item.maximum())-slot.amount()));
     next[i]=new Slot(slot.material(),slot.amount()+add,slot.maximum(),true); left-=add;
    }
   }
   for(int i=0;i<next.length && left>0;i++) if(next[i]==null) {
    int add=Math.min(left,item.maximum()); next[i]=new Slot(item.material(),add,item.maximum(),true); left-=add;
   }
   if(left>0) return null;
  }
  return next;
 }
 static boolean has(Slot[] source, Item item) {
  long count=0; for(Slot slot:source) if(slot!=null && slot.plain() && slot.material().equals(item.material())) count+=slot.amount();
  return count>=item.amount();
 }
 static Slot[] remove(Slot[] source, Item item) {
  if(!has(source,item)) return null;
  Slot[] next=source.clone(); int left=item.amount();
  for(int i=0;i<next.length && left>0;i++) {
   Slot slot=next[i]; if(slot!=null && slot.plain() && slot.material().equals(item.material())) {
    int take=Math.min(left,slot.amount()); left-=take;
    next[i]=take==slot.amount()?null:new Slot(slot.material(),slot.amount()-take,slot.maximum(),true);
   }
  }
  return next;
 }
}
