package nate.reading;

import java.util.List;

/**
 * @author Nate Chambers
 */
public interface Template {
  public List<MUCEntity> getSlotEntities(int index);
  public boolean isOptional();
  public void put(String key, String value);
  public String get(String key);
  public List<MUCEntity> getEntitiesByType(String[] types);
  public List<MUCEntity> getMainEntities();
}
