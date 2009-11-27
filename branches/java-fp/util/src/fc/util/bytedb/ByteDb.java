package fc.util.bytedb;

import java.io.File;
import java.util.Enumeration;
/**
 * A common interface for flat file databases such as Sdbm and Jdbm.
 * The purpose of this interface is to allow easy changing 
 * between different storage methods.
 * @author Eemil Lagerspetz
 *
 */
public interface ByteDb {
  /**
   * Creates a new ByteDb. Since a constructor cannot be specified in an interface,
   * this method is provided. Creates a new instance and returns it.
   * Usage: ByteDb b = new SomethingByteDb();
   * b = b.create(foo, false); 
   * @param f the directory to create the db in
   * @param cached whether the db is internally cached (not implemented yet)
   * @return a new ByteDb ready for storing items.
   */
  public ByteDb create(File f, boolean cached);
  public byte[] lookup(byte[] key);
  public void delete(byte[] key);
  public void update(byte[] key, byte[] value);
  public void close();
  public Enumeration keys();
}
