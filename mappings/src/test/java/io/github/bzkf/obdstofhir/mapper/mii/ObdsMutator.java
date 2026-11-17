package io.github.bzkf.obdstofhir.mapper.mii;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import de.basisdatensatz.obds.v3.OBDS;
import de.basisdatensatz.obds.v3.OBDS.MengePatient.Patient.MengeMeldung.Meldung;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.jspecify.annotations.Nullable;

/**
 * Changes parsed oBDS the way a structure-aware fuzzer would, letting the fuzzer's data decide what
 * to change. Byte-level mutations of XML rarely stay valid against the XSD, while these mostly do,
 * because the values they use come from other oBDS:
 *
 * <ul>
 *   <li>replace a value or a whole element with the same field's value from any of the seeds
 *   <li>remove a value, a list element or all elements of a list
 *   <li>duplicate, reorder or add list elements, e.g. Meldungen of other seeds
 *   <li>shift a date by up to ten years
 *   <li>set a text to an arbitrary string
 * </ul>
 *
 * <p>It works on any class of the generated oBDS model through reflection, so it needs no changes
 * when the model changes.
 */
final class ObdsMutator {
  private static final Package OBDS_PACKAGE = OBDS.class.getPackage();
  private static final int WHOLE_LIST = -1;
  private static final int NOT_A_LIST = -2;
  private static final int FREITEXT_ID_MAX_LENGTH = 50;
  private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();
  private static final DatatypeFactory DATATYPE_FACTORY = createDatatypeFactory();

  private enum Operation {
    REPLACE,
    REMOVE,
    DUPLICATE,
    SWAP,
    INSERT,
    SHIFT_DATE,
    SET_TEXT,
  }

  /**
   * A position in the model that can be changed: a field, one element of a list field, or a list
   * field as a whole.
   */
  private record Slot(Object owner, Field field, int index) {
    String key() {
      return field.getDeclaringClass().getName() + "#" + field.getName();
    }

    @Nullable Object value() {
      var value = get(field, owner);
      return index >= 0 ? ((List<?>) value).get(index) : value;
    }

    @SuppressWarnings("unchecked")
    List<Object> list() {
      var list = (List<Object>) get(field, owner);
      if (list == null) {
        list = new ArrayList<>();
        set(field, owner, list);
      }
      return list;
    }

    void replace(@Nullable Object value) {
      if (index >= 0) {
        list().set(index, value);
      } else {
        set(field, owner, value);
      }
    }
  }

  private final List<OBDS> seeds;
  private final Map<String, List<Object>> valuesByField = new HashMap<>();
  private int copies;

  ObdsMutator(List<OBDS> seeds) {
    this.seeds = List.copyOf(seeds);
    for (var seed : seeds) {
      forEachSlot(
          seed,
          slot -> {
            var value = slot.value();
            if (slot.index() != WHOLE_LIST && value != null) {
              valuesByField.computeIfAbsent(slot.key(), key -> new ArrayList<>()).add(value);
            }
          });
    }
  }

  /** Returns a copy of one of the seeds with the changes the data decides on. */
  OBDS mutate(FuzzedDataProvider data) {
    var obds = deepCopy(seeds.get(data.consumeInt(0, seeds.size() - 1)));
    var mutations = data.consumeInt(0, 8);
    for (int i = 0; i < mutations; i++) {
      var slots = new ArrayList<Slot>();
      forEachSlot(obds, slots::add);
      apply(
          slots.get(data.consumeInt(0, slots.size() - 1)),
          data.pickValue(Operation.values()),
          data);
    }
    return obds;
  }

  private void apply(Slot slot, Operation operation, FuzzedDataProvider data) {
    var value = slot.index() == WHOLE_LIST ? null : slot.value();
    switch (operation) {
      case REPLACE -> {
        var candidates = valuesByField.get(slot.key());
        if (slot.index() != WHOLE_LIST && candidates != null) {
          slot.replace(deepCopy(data.pickValue(candidates)));
        }
      }
      case REMOVE -> {
        if (slot.index() >= 0) {
          slot.list().remove(slot.index());
        } else if (slot.index() == WHOLE_LIST) {
          slot.list().clear();
        } else {
          slot.replace(null);
        }
      }
      case DUPLICATE -> {
        if (slot.index() >= 0) {
          var list = slot.list();
          list.add(data.consumeInt(0, list.size()), withNewId(deepCopy(value)));
        }
      }
      case SWAP -> {
        if (slot.index() >= 0 && slot.list().size() > 1) {
          var list = slot.list();
          Collections.swap(list, slot.index(), data.consumeInt(0, list.size() - 1));
        }
      }
      case INSERT -> {
        var candidates = valuesByField.get(slot.key());
        if (slot.index() != NOT_A_LIST && candidates != null) {
          var list = slot.list();
          list.add(
              data.consumeInt(0, list.size()), withNewId(deepCopy(data.pickValue(candidates))));
        }
      }
      case SHIFT_DATE -> {
        if (value instanceof XMLGregorianCalendar date) {
          var shifted = (XMLGregorianCalendar) date.clone();
          var days = data.consumeInt(-3650, 3650);
          shifted.add(DATATYPE_FACTORY.newDurationDayTime(days >= 0, Math.abs(days), 0, 0, 0));
          slot.replace(shifted);
        }
      }
      case SET_TEXT -> {
        if (slot.index() == NOT_A_LIST && slot.field().getType() == String.class) {
          // without control characters, which XML can't contain
          slot.replace(data.consumeAsciiString(60).replaceAll("\\p{Cntrl}", ""));
        }
      }
    }
  }

  /** A copied Meldung needs a new Meldung_ID, as the XSD requires them to be unique. */
  private @Nullable Object withNewId(@Nullable Object copy) {
    if (copy instanceof Meldung meldung) {
      var suffix = "-" + ++copies;
      var id = meldung.getMeldungID();
      meldung.setMeldungID(
          id.substring(0, Math.min(id.length(), FREITEXT_ID_MAX_LENGTH - suffix.length()))
              + suffix);
    }
    return copy;
  }

  private static void forEachSlot(Object node, Consumer<Slot> action) {
    for (var field : fieldsOf(node.getClass())) {
      var value = get(field, node);
      if (List.class.isAssignableFrom(field.getType())) {
        action.accept(new Slot(node, field, WHOLE_LIST));
        var list = value == null ? List.of() : (List<?>) value;
        for (int i = 0; i < list.size(); i++) {
          action.accept(new Slot(node, field, i));
          if (isModel(list.get(i))) {
            forEachSlot(list.get(i), action);
          }
        }
      } else {
        action.accept(new Slot(node, field, NOT_A_LIST));
        if (isModel(value)) {
          forEachSlot(value, action);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  static <T> T deepCopy(T value) {
    if (value instanceof XMLGregorianCalendar date) {
      return (T) date.clone();
    }
    if (value instanceof List<?> list) {
      var copy = new ArrayList<>(list.size());
      list.forEach(element -> copy.add(deepCopy(element)));
      return (T) copy;
    }
    if (!isModel(value)) {
      // String, enums, numbers and booleans are immutable
      return value;
    }
    try {
      var copy = value.getClass().getDeclaredConstructor().newInstance();
      for (var field : fieldsOf(value.getClass())) {
        set(field, copy, deepCopy(get(field, value)));
      }
      return (T) copy;
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException("Failed to copy " + value.getClass(), e);
    }
  }

  private static boolean isModel(@Nullable Object value) {
    return value != null
        && value.getClass().getPackage() == OBDS_PACKAGE
        && !value.getClass().isEnum();
  }

  /** The instance fields of the class and its superclasses, in a stable order. */
  private static List<Field> fieldsOf(Class<?> type) {
    return FIELDS.computeIfAbsent(
        type,
        t -> {
          var fields = new ArrayList<Field>();
          for (var c = t; c != null && c != Object.class; c = c.getSuperclass()) {
            var declared = new ArrayList<Field>();
            for (var field : c.getDeclaredFields()) {
              if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                field.setAccessible(true);
                declared.add(field);
              }
            }
            declared.sort(Comparator.comparing(Field::getName));
            fields.addAll(0, declared);
          }
          return List.copyOf(fields);
        });
  }

  private static @Nullable Object get(Field field, Object owner) {
    try {
      return field.get(owner);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void set(Field field, Object owner, @Nullable Object value) {
    try {
      field.set(owner, value);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  private static DatatypeFactory createDatatypeFactory() {
    try {
      return DatatypeFactory.newInstance();
    } catch (DatatypeConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }
}
