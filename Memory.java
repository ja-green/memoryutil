package com.memoryutils;

/*
 * (C) Copyright 2017 Jack Green.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * This class consists of helper methods for using {@code sun.misc.Unsafe} to
 * access and manipulate off-heap memory.
 *
 * <p>Methods in this class have been designed with performance in mind and therefore all
 * checks use only the assert keyword. With assertions enabled, illegal memory access
 * will throw an {@code AssertionError}. Without assertions illegal memory access
 * will potentially crash the JVM. Assertions can be enabled in runtime using the
 * {@code -ea} switch in the JVM arguments.
 *
 * <p>They have been designed to emulate C's {@code stdlib.h} library functions:
 * <ul>
 *   <li>malloc   </li>
 *   <li>calloc   </li>
 *   <li>realloc  </li>
 *   <li>free     </li>
 * </ul>
 *
 * and C's {@code string.h} library functions:
 * <ul>
 *   <li>memchr   </li>
 *   <li>memcpy   </li>
 *   <li>memcmp   </li>
 *   <li>memset   </li>
 * </ul>
 *
 * <p>Pointers provided by this class support pointer arithmetic
 * and can be "dereferenced" using methods in this class.
 *
 * <p><b>This class uses {@code sun.misc.Unsafe}. It is very possible to crash the JVM
 * with improper use so use at your own risk.
 *
 * @author  Jack Green (ja-green)
 * @version 1.0        (15/12/17)
 */
public final class Memory {
  private static final Unsafe   UNSAFE;
  private static final boolean  JVM_64;

  /*
   * Public variables for easy allocation of different byte amounts
   * for use with malloc, calloc and realloc.
   */
  public static final long      BYTE;
  public static final long      KILOBYTE;
  public static final long      MEGABYTE;
  public static final long      GIGABYTE;

  static {
    try {
      Field field = Unsafe.class.getDeclaredField("theUnsafe");
      field.setAccessible(true);

      UNSAFE   = (Unsafe) field.get(null);
      JVM_64   = UNSAFE.addressSize() == 8;

      BYTE     = 1;
      KILOBYTE = 1024 * BYTE;
      MEGABYTE = 1024 * KILOBYTE;
      GIGABYTE = 1024 * MEGABYTE;

    } catch (Exception ex) {
      throw new AssertionError(ex);
    }
  }

  /*
   * Suppresses default constructor, ensuring non-instantiability.
   */
  private Memory() {}

  /**
   * Allocates {@code bytes} bytes to off-heap memory.
   * Returns the lowest byte in the allocated memory block
   * which is suitably aligned for any object type.
   *
   * <p>The allocated bytes will be uninitialised and
   * therefore will generally be garbage.
   *
   * <p>If a null pointer is passed as {@code pointer},
   * no action will be performed.
   *
   * <p>The JVM wil be unable to GC the bytes allocated here
   * so they must be released using {@link #free(long)} to
   * avoid memory leaks. Resize this chunk of memory using
   * {@link #realloc(long, long)}.
   *
   * @param   bytes the size (in bytes) to allocate
   * @return  a pointer to the lowest byte
   *          in the allocated memory block
   * @see     #calloc(long)
   * @see     #realloc(long, long)
   */
  public static long malloc(long bytes) {
    assert bytes >= 0;

    return UNSAFE.allocateMemory(bytes);
  }

  /**
   * Allocates <code>bytes</code> bytes to off-heap memory.
   * Returns the lowest byte in the allocated memory block
   * which is suitably aligned for any object type.
   *
   * <p>The allocated bytes will be initialised to null bytes.
   *
   * <p>If a null pointer is passed as {@code pointer},
   * no action will be performed.
   *
   * <p>The JVM wil be unable to GC the bytes allocated here
   * so they must be released using {@link #free(long)} to
   * avoid memory leaks. Resize this chunk of memory using
   * {@link #realloc(long, long)}.
   *
   * @param   bytes the size (in bytes) to allocate
   * @return  a pointer to the lowest byte
   *          in the allocated memory block
   * @see     #malloc(long)
   * @see     #realloc(long, long)
   */
  public static long calloc(long bytes) {
    assert bytes >= 0;

    long pointer =  UNSAFE.allocateMemory(bytes);

    UNSAFE.setMemory(pointer, bytes, (byte) 0);

    return pointer;
  }

  /**
   * Re-sizes a current block of off-heap memory pointed to
   * by {@code pointer} to the new size, {@code bytes},
   * specified in bytes. Returns the lowest byte in the allocated
   * memory block which is suitably aligned for any object type.
   *
   * <p>The new allocated bytes will be uninitialised and
   * therefore will generally be garbage.
   *
   * <p>If a null pointer is passed as {@code pointer},
   * realloc behaves just like {@link #malloc(long)}. If
   * the requested new size is zero, a null pointer will
   * be returned.
   *
   * <p>The JVM wil be unable to GC the bytes allocated here
   * so they must be released using {@link #free(long)} to
   * avoid memory leaks. Resize this chunk of memory using
   * {@link #realloc(long, long)}.
   **
   * @param   pointer the pointer to the pre-allocated block of memory
   * @param   bytes   the size (in bytes) to resize the block to
   * @return  a pointer to the lowest byte in the new
   *          allocated memory block
   * @see     #malloc(long)
   * @see     #calloc(long)
   */
  public static long realloc(long pointer, long bytes) {
    assert pointer != 0;

    return UNSAFE.reallocateMemory(pointer, bytes);
  }

  /**
   * Frees the memory allocated at the address {@code pointer} as
   * allocated by {@link #malloc(long)}, {@link #calloc(long)}
   * or {@link #realloc(long, long)}.
   *
   * <p>If a null pointer is passed as {@code pointer},
   * no action will be performed.
   *
   * @param pointer the pointer to a block of allocated heap memory to free.
   * @see   #malloc(long)
   * @see   #calloc(long)
   * @see   #realloc(long, long)
   */
  public static void free(long pointer) {
    if (pointer != 0) UNSAFE.freeMemory(pointer);
  }

  private static Object min_instance(Class clazz) {
    try {
      if (clazz.isPrimitive()) switch (clazz.getName()) {

        case "long"   : return (long)    0;
        case "double" : return (double)  0;
        case "int"    : return           0;
        case "float"  : return (float)   0;
        case "char"   : return (char)    0;
        case "short"  : return (short)   0;
        case "byte"   : return (byte)    0;
        case "boolean": return false;

      } else if (clazz.isArray()) switch (clazz.getName()) {

        case "[L"     : return new long[]    {0};
        case "[D"     : return new double[]  {0};
        case "[I"     : return new int[]     {0};
        case "[F"     : return new float[]   {0};
        case "[C"     : return new char[]    {0};
        case "[S"     : return new short[]   {0};
        case "[B"     : return new byte[]    {0};
        case "[Z"     : return new boolean[] {false};
      }

      Constructor ctor          = clazz.getDeclaredConstructors()[0];
      int ctor_params_cnt       = ctor.getParameterCount();
      Class[] ctor_param_types  = ctor.getParameterTypes();
      Object[] ctor_params      = new Object[ctor_params_cnt];

      for (int i = 0; i < ctor_params_cnt; i++) {
        switch (ctor_param_types[i].getName()) {

          case "java.lang.Long"     :  case "long"   : ctor_params[i] = (long)     0;   break;
          case "java.lang.Double"   :  case "double" : ctor_params[i] = (double)   0;   break;
          case "java.lang.Integer"  :  case "int"    : ctor_params[i] =            0;   break;
          case "java.lang.Float"    :  case "float"  : ctor_params[i] = (float)    0;   break;
          case "java.lang.Character":  case "char"   : ctor_params[i] = (char)     0;   break;
          case "java.lang.Short"    :  case "short"  : ctor_params[i] = (short)    0;   break;
          case "java.lang.Byte"     :  case "byte"   : ctor_params[i] = (byte)     0;   break;
          case "java.lang.Boolean"  :  case "boolean": ctor_params[i] = false;          break;

          case "[L"     : ctor_params[i] = new long[]    {0};      break;
          case "[D"     : ctor_params[i] = new double[]  {0};      break;
          case "[I"     : ctor_params[i] = new int[]     {0};      break;
          case "[F"     : ctor_params[i] = new float[]   {0};      break;
          case "[C"     : ctor_params[i] = new char[]    {0};      break;
          case "[S"     : ctor_params[i] = new short[]   {0};      break;
          case "[B"     : ctor_params[i] = new byte[]    {0};      break;
          case "[Z"     : ctor_params[i] = new boolean[] {false};  break;

          case "java.lang.String" : ctor_params[i] = "";            break;

          default       : ctor_params[i] = ctor_param_types[i].cast(new Object());           break;
        }
      }

      for (Object ctor_param : ctor_params) {
        System.out.println("param: " + ctor_param.getClass().getName());
      }

      return ctor.newInstance(ctor_params);

    } catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }

  /**
   * Gets the size of the Object, {@code obj} in bytes.
   *
   * <p>If the Object is an array, the size will be multiplied
   * by the length of the array to ensure enough space for all elements.
   *
   * <p>Should be used with {@link #malloc(long)}, {@link #calloc(long)} or {@link #realloc(long, long)}
   * to allocate the correct amount of bytes for the specific Object
   *
   * @param   obj the Object to size
   * @return  the size of the Object in bytes
   */
  public static long sizeof(Object obj) {
    return sizeof(obj.getClass(), obj);
  }

  /**
   * Gets the size of a class, {@code c} in bytes.
   *
   * <p>Should be used with {@link #malloc(long)}, {@link #calloc(long)}
   * or {@link #realloc(long, long)} to allocate the correct amount
   * of bytes for the specific Object
   *
   * <p>The whole class always pads to the next multiple
   * of 8 bytes, so {@link #round(long)} is called to handle this.
   *
   * @param   clazz the class to size
   * @return  the size of the class in bytes
   */
  public static long sizeof(Class clazz) {
    return sizeof(clazz, min_instance(clazz));
  }

  private static long sizeof(Class clazz, Object o) {
    long mem_offset = header_size(clazz);

    if (o == null || clazz == null) return 0;

    if (clazz.isPrimitive()) switch (clazz.getName()) {

      case "long": case "double" : return 8;
      case "int" : case "float"  : return 4;
      case "char": case "short"  : return 2;
      case "byte": case "boolean": return 1;

      default:                     return 8;

    } if (clazz.isArray()) switch (clazz.getName()) {

      case "[L":   return 8 * ((long[])    o).length;
      case "[D":   return 8 * ((double[])  o).length;
      case "[I":   return 4 * ((int[])     o).length;
      case "[F":   return 4 * ((float[])   o).length;
      case "[C":   return 2 * ((char[])    o).length;
      case "[S":   return 2 * ((short[])   o).length;
      case "[B":   return 1 * ((byte[])    o).length;
      case "[Z":   return 1 * ((boolean[]) o).length;

      default:     return 8;

    } if (clazz.isEnum()) return 4;

    long obj_offset = 0;
    Field[] fields  = clazz.getDeclaredFields();

    for (int i = 0; i < fields.length; i++) {
      if ((fields[i].getModifiers() & Modifier.STATIC) == 0) {
        obj_offset = UNSAFE.objectFieldOffset(fields[i]);

        switch (fields[i].getType().getName()) {

          case "long":  case "double" : mem_offset += 8;  break;
          case "int" :  case "float"  : mem_offset += 4;  break;
          case "char":  case "short"  : mem_offset += 2;  break;
          case "byte":  case "boolean": mem_offset += 1;  break;

          case "[L":    mem_offset += 8 * ((long[])    UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[D":    mem_offset += 8 * ((double[])  UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[I":    mem_offset += 4 * ((int[])     UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[F":    mem_offset += 4 * ((float[])   UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[C":    mem_offset += 2 * ((char[])    UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[S":    mem_offset += 2 * ((short[])   UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[B":    mem_offset += 1 * ((byte[])    UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[Z":    mem_offset += 1 * ((boolean[]) UNSAFE.getObject(o, obj_offset)).length;   break;

          default:      mem_offset += sizeof(fields[i].getType(), UNSAFE.getObject(o, obj_offset))
                                    - header_size(fields[i].getType());                           break;

        }
      }
    }

    return round(mem_offset);
  }

  /**
   * Gets the pointer of the first native memory address
   * containing the byte, {@code val}, starting from the
   * address, {@code addr} and stopping at the address {@code len}
   * from the starting address.
   *
   * @param   pointer  the address to start at.
   * @param   val      the byte to look for.
   * @param   len      the length from the starting address to check to
   * @return  a pointer to the address containing the byte, {@code val},
   *          a null pointer if the byte cannot be found.
   */
  public static long memchr(long pointer, byte val, int len) {
    assert pointer != 0;

    for (int i = 0; i <= len; i++)
      if ((memget_b(pointer + i) ^ val) == 0)
        return pointer + i;

    return 0;
  }

  /**
   * Copies {@code len} bytes from the address at
   * pointer {@code src} to the address at pointer {@code dest}.
   *
   * <p>If the copy length, {@code len} is less than 5, this
   * performs a simple get and put operation. If it is 5 or greater,
   * it calls {@code Unsafe.copyMemory}. This is a performance
   * enhancement as the get and put operation is faster for small
   * copy lengths, peaking around a length of 5.
   *
   * @param   dest the destination pointer to copy to
   * @param   src  the source pointer to copy from
   * @param   len  the amount of bytes to copy
   */
  public static void memcpy(long dest, long src, int len) {
    assert dest != 0 || src  != 0;

    if (len < 5)
      for (int i = 0; i < len; i++)
        memput(dest + i, memget_b(src + i));

    else UNSAFE.copyMemory(dest, src, len);
  }

  /**
   * Sets the first {@code len} bytes of the block of memory pointed by
   * {@code addr} to the specified value, {@code val}.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   val     the value to be set
   * @param   len     the number of bytes to be set to the specified value, {@code val}
   */
  public static void memset(long pointer, byte val, int len) {
    assert pointer != 0;

    UNSAFE.setMemory(pointer, len, val);
  }

  /**
   * Sets the bytes of the block of memory pointed by {@code addr}
   * to the long values specified in the array, {@code buffer}
   * in the order in which they are specified.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   buffer  the array of values to be set
   */
  public static void memset(long pointer, long[] buffer) {
    assert pointer != 0 || buffer != null;

    for (int i = 0; i < buffer.length << 3; i += 8)
      UNSAFE.putLong(pointer + i, buffer[i >> 3]);
  }

  /**
   * Sets the bytes of the block of memory pointed by {@code addr}
   * to the double values specified in the array, {@code buffer}
   * in the order in which they are specified.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   buffer  the array of values to be set
   */
  public static void memset(long pointer, double[] buffer) {
    assert pointer != 0 || buffer != null;

    for (int i = 0; i < buffer.length << 3; i += 8)
      UNSAFE.putDouble(pointer + i, buffer[i >> 3]);
  }

  /**
   * Sets the bytes of the block of memory pointed by {@code addr}
   * to the int values specified in the array, {@code buffer}
   * in the order in which they are specified.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   buffer  the array of values to be set
   */
  public static void memset(long pointer, int[] buffer) {
    assert pointer != 0 || buffer != null;

    for (int i = 0; i < buffer.length << 2; i += 4)
      UNSAFE.putInt(pointer + i, buffer[i >> 2]);
  }

  /**
   * Sets the bytes of the block of memory pointed by {@code addr}
   * to the float values specified in the array, {@code buffer}
   * in the order in which they are specified.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   buffer  the array of values to be set
   */
  public static void memset(long pointer, float[] buffer) {
    assert pointer != 0 || buffer != null;

    for (int i = 0; i < buffer.length << 2; i += 4)
      UNSAFE.putFloat(pointer + i, buffer[i >> 2]);
  }

  /**
   * Sets the bytes of the block of memory pointed by {@code addr}
   * to the short values specified in the array, {@code buffer}
   * in the order in which they are specified.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   buffer  the array of values to be set
   */
  public static void memset(long pointer, short[] buffer) {
    assert pointer != 0 || buffer != null;

    for (int i = 0; i < buffer.length << 1; i += 2)
      UNSAFE.putShort(pointer + i, buffer[i >> 1]);
  }

  /**
   * Sets the bytes of the block of memory pointed by {@code addr}
   * to the byte values specified in the array, {@code buffer}
   * in the order in which they are specified.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   buffer  the array of values to be set
   */
  public static void memset(long pointer, byte[] buffer) {
    assert pointer != 0 || buffer != null;

    for (int i = 0; i < buffer.length; i++)
      UNSAFE.putByte(pointer + i, buffer[i]);
  }

  /**
   * Sets the bytes of the block of memory pointed by {@code addr}
   * to the char values specified in the array, {@code buffer}
   * in the order in which they are specified.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   buffer  the array of values to be set
   */
  public static void memset(long pointer, char[] buffer) {
    assert pointer != 0 || buffer != null;

    for (int i = 0; i < buffer.length; i++)
      UNSAFE.putChar(pointer + i, buffer[i]);
  }

  /**
   * Sets the bytes of the block of memory pointed by {@code addr}
   * to the boolean values specified in the array, {@code buffer}
   * in the order in which they are specified.
   *
   * @param   pointer the pointer to the block of memory to start setting at
   * @param   buffer  the array of values to be set
   */
  public static void memset(long pointer, boolean[] buffer) {
    assert pointer != 0 || buffer != null;

    for (int i = 0; i < buffer.length; i++)
      UNSAFE.putByte(pointer + i, (buffer[i]) ? (byte) 1 : (byte) 0);
  }

  /**
   * Puts the byte, {@code val} at the address pointed to by {@code pointer}.
   *
   * @param   pointer the memory location to put the specified byte
   * @param   val     the byte to put
   */
  public static void memput(long pointer, byte val) {
    assert pointer != 0;

    UNSAFE.putByte(pointer, val);
  }

  /**
   * Puts the short, {@code val} at the address pointed to by {@code pointer}.
   *
   * @param   pointer the memory location to put the specified short
   * @param   val     the short to put
   */
  public static void memput(long pointer, short val) {
    assert pointer != 0;

    UNSAFE.putShort(pointer, val);
  }

  /**
   * Puts the int, {@code val} at the address pointed to by {@code pointer}.
   *
   * @param   pointer the memory location to put the specified int
   * @param   val     the int to put
   */
  public static void memput(long pointer, int val) {
    assert pointer != 0;

    UNSAFE.putInt(pointer, val);
  }

  /**
   * Puts the long, {@code val} at the address pointed to by {@code pointer}.
   *
   * <p>This is equivalent of dereferencing a pointer in C
   *
   * @param   pointer the memory location to put the specified long
   * @param   val     the long to put
   */
  public static void memput(long pointer, long val) {
    assert pointer != 0;

    UNSAFE.putLong(pointer, val);
  }

  ////////////////////////////////////////////////////////////////////////
  // BEGIN TEST
  ////////////////////////////////////////////////////////////////////////

  public static Object memget_field(long pointer, Class clazz, String name) {
    assert pointer != 0;

    Field[] fields = clazz.getDeclaredFields();

    for (int i = 0; i < fields.length; i++) {
      if (fields[i].getName().equals(name)) {
        return memget_field(
          pointer,
          clazz,
          i
        );
      }
    }
    return null;
  }

  public static Object memget_field(long pointer, Class clazz, int index) {
    assert pointer != 0;

    long i = 0, offset = header_size(clazz);

    for (Field f : clazz.getDeclaredFields()) {
      if (i == index) switch (f.getType().getName()) {
          case "long":  case "double" : return memget_l(pointer + offset);
          case "int" :  case "float"  : return memget_i(pointer + offset);
          case "char":  case "short"  : return memget_s(pointer + offset);
          case "byte":  case "boolean": return memget_b(pointer + offset);

          default:                      return memget_l(pointer + offset);
        }

      else switch (f.getType().getName()) {
          case "long":  case "double" : offset += 8;   break;
          case "int" :  case "float"  : offset += 4;   break;
          case "char":  case "short"  : offset += 2;   break;
          case "byte":  case "boolean": offset += 1;   break;

          default:                      offset += 8;   break;

      } i++;
    } return null;
  }

  public static void memput_field(long pointer, Class clazz, int index, Object val) {
    assert pointer != 0;

    long i = 0, offset = header_size(clazz);

    for (Field f : clazz.getDeclaredFields()) {
      if (i == index) {
        switch (f.getType().getName()) {
          case "long":  case "double" : memput(pointer + offset, (long)  val);  break;
          case "int" :  case "float"  : memput(pointer + offset, (int)   val);  break;
          case "char":  case "short"  : memput(pointer + offset, (short) val);  break;
          case "byte":  case "boolean": memput(pointer + offset, (byte)  val);  break;
          default:                      memput(pointer + offset, (long)  val);  break;
        }

      } else {
        switch (f.getType().getName()) {
          case "long":  case "double" : offset += 8;   break;
          case "int" :  case "float"  : offset += 4;   break;
          case "char":  case "short"  : offset += 2;   break;
          case "byte":  case "boolean": offset += 1;   break;
          default:                      offset += 8;   break;
        }
      }
      i++;
    }
  }

  /**
   * Puts the Object, {@code val} at the address pointed to by {@code pointer}.
   *
   * <p>This is equivalent of dereferencing a pointer in C
   *
   * @param   pointer the memory location to put the specified Object
   * @param   clazz   the Object to put
   */
  public static void memput(long pointer, Class clazz) {
    memput(null, pointer, min_instance(clazz));
  }

  public static void memput(long pointer, Object o) {
    memput(null, pointer, o);
  }

  public static void memput(Class parent, long pointer, Object o) {
    assert pointer != 0;

    Class clazz     = o.getClass();
    Field[] fields  = clazz.getDeclaredFields();
    long mem_offset = (parent == null) ? header_size(clazz) : 0;
    long obj_offset = 0;

    for (int i = 0; i < fields.length; i++) {
      if ((fields[i].getModifiers() & Modifier.STATIC) == 0) {
        obj_offset = UNSAFE.objectFieldOffset(fields[i]);
        /*System.out.println(
           "Putting " + fields[i].getType().getName()
             + " from obj offset " + obj_offset
             + " at pointer " + (pointer + mem_offset)
             + ", offset " + mem_offset
         );*/

        switch (fields[i].getType().getName()) {
          case "long":  case "double" : memput(pointer + mem_offset, UNSAFE.getLong(o, obj_offset));   break;
          case "int" :  case "float"  : memput(pointer + mem_offset, UNSAFE.getInt(o, obj_offset));    break;
          case "char":  case "short"  : memput(pointer + mem_offset, UNSAFE.getShort(o, obj_offset));  break;
          case "byte":  case "boolean": memput(pointer + mem_offset, UNSAFE.getByte(o, obj_offset));   break;

          case "[L":    memset(pointer + mem_offset, (long[])     UNSAFE.getObject(o, obj_offset));    break;
          case "[D":    memset(pointer + mem_offset, (double[])   UNSAFE.getObject(o, obj_offset));    break;
          case "[I":    memset(pointer + mem_offset, (int[])      UNSAFE.getObject(o, obj_offset));    break;
          case "[F":    memset(pointer + mem_offset, (float[])    UNSAFE.getObject(o, obj_offset));    break;
          case "[C":    memset(pointer + mem_offset, (char[])     UNSAFE.getObject(o, obj_offset));    break;
          case "[S":    memset(pointer + mem_offset, (short[])    UNSAFE.getObject(o, obj_offset));    break;
          case "[B":    memset(pointer + mem_offset, (byte[])     UNSAFE.getObject(o, obj_offset));    break;
          case "[Z":    memset(pointer + mem_offset, (boolean[])  UNSAFE.getObject(o, obj_offset));    break;

          default: memput(fields[i].getType(), pointer + mem_offset, UNSAFE.getObject(o, obj_offset)); break;
        }

        switch (fields[i].getType().getName()) {
          case "long":  case "double" : mem_offset += 8;  break;
          case "int" :  case "float"  : mem_offset += 4;  break;
          case "char":  case "short"  : mem_offset += 2;  break;
          case "byte":  case "boolean": mem_offset += 1;  break;

          case "[L":    mem_offset += 8 * ((long[])    UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[D":    mem_offset += 8 * ((double[])  UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[I":    mem_offset += 4 * ((int[])     UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[F":    mem_offset += 4 * ((float[])   UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[C":    mem_offset += 2 * ((char[])    UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[S":    mem_offset += 2 * ((short[])   UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[B":    mem_offset += 1 * ((byte[])    UNSAFE.getObject(o, obj_offset)).length;   break;
          case "[Z":    mem_offset += 1 * ((boolean[]) UNSAFE.getObject(o, obj_offset)).length;   break;

          default:      mem_offset += sizeof(UNSAFE.getObject(o, obj_offset))
                                    - header_size(fields[i].getType());  break;
        }
      }
    }
  }

  public static Object memget_object(long pointer, Class clazz) {
    assert pointer != 0;

    Object instance;

    try {
      instance = UNSAFE.allocateInstance(clazz);

    } catch (InstantiationException e) {
      return null;
    }

    do {
      for (Field f : clazz.getDeclaredFields()) {
        if ((f.getModifiers() & Modifier.STATIC) == 0) {
          long offset = UNSAFE.objectFieldOffset(f);
          if (f.getType() == long.class) {
            UNSAFE.putLong(instance, offset, UNSAFE.getLong(pointer + offset));
          } else if (f.getType() == int.class) {
            UNSAFE.putLong(instance, offset, UNSAFE.getInt(pointer + offset));
          } else {
            throw new UnsupportedOperationException();
          }
        }
      }
    } while ((clazz = clazz.getSuperclass()) != null);
    return instance;
  }

  ////////////////////////////////////////////////////////////////////////
  // END TEST
  ////////////////////////////////////////////////////////////////////////

  /**
   * Gets a byte located at the address pointed to by {@code pointer}.
   *
   * <p>This is equivalent of dereferencing a pointer in C
   *
   * @param   pointer the memory location to get the byte from
   */
  public static byte memget_b(long pointer) {
    assert pointer != 0;

    return UNSAFE.getByte(pointer);
  }

  /**
   * Gets a short located at the address pointed to by {@code pointer}.
   *
   * <p>This is equivalent of dereferencing a pointer in C
   *
   * @param   pointer the memory location to get the short from
   */
  public static short memget_s(long pointer) {
    assert pointer != 0;

    return UNSAFE.getShort(pointer);
  }

  /**
   * Gets an int located at the address pointed to by {@code pointer}.
   *
   * <p>This is equivalent of dereferencing a pointer in C
   *
   * @param   pointer the memory location to get the int from
   */
  public static int memget_i(long pointer) {
    assert pointer != 0;

    return UNSAFE.getInt(pointer);
  }

  /**
   * Gets a long located at the address pointed to by {@code addr}.
   *
   * <p>This is equivalent of dereferencing a pointer in C
   *
   * @param   pointer the memory location to get the long from
   */
  public static long memget_l(long pointer) {
    assert pointer != 0;

    return UNSAFE.getLong(pointer);
  }

  /**
   * Gets an array of bytes located at the address pointed to by {@code pointer},
   * ending at the first null byte.
   *
   * <p>The results are undefined if there is no null byte present.
   *
   * <p>This is equivalent of dereferencing a pointer in C.
   *
   * @param   pointer the memory location to get the bytes from.
   */
  public static byte[] memget_a(long pointer) {
    assert pointer != 0;

    long end = memchr(pointer, (byte) 0, 100);
    long len = end - pointer;

    return memget_a(pointer, (int) len);
  }

  /**
   * Gets an array of bytes located at the address pointed to by {@code pointer},
   * ending at the address {@code len} bytes from the specified address.
   *
   * <p>This is equivalent of dereferencing a pointer in C.
   *
   * @param   pointer the memory location to get the bytes from
   * @param   len     the length of bytes to get from the specified address
   */
  public static byte[] memget_a(long pointer, int len) {
    assert pointer != 0 && len >= 0;

    byte[] result = new byte[len];

    for (int i = 0; i < len; i++)
      result[i] = memget_b(pointer + i);

    return result;
  }

  /**
   * Compares the first {@code len} bytes of the block of memory at {@code pointer1}
   * to the first {@code len} bytes of the block of memory at {@code pointer2}, returning true
   * if they all match or false if they do not.
   *
   * <p>This processes as many bytes as possible at a time for performance gain.
   * For example, if 16 bytes were to be compared, it would process the bytes in 2 blocks
   * of 8 instead of one at a time.
   *
   * @param   pointer1  the memory location to compare against
   * @param   pointer2  the memory location to compare to
   * @param   len       the length of bytes to compare
   * @return  {@code true} if the bytes match, {@code false} if not.
   */
  public static boolean memcmp(long pointer1, long pointer2, int len) {
    assert pointer1 != 0
      &&   pointer2 != 0
      &&   len      >= 0;

    if ((len & 8) == 0) return memcmp_l(pointer1, pointer2, len);
    if ((len & 4) == 0) return memcmp_i(pointer1, pointer2, len);
    if ((len & 2) == 0) return memcmp_s(pointer1, pointer2, len);
    else                return memcmp_b(pointer1, pointer2, len);
  }

  /**
   * Helper method for {@link #memcmp(long, long, int)}. Compares bytes one by
   * one in the case that {@code len} passed to {@link #memcmp(long, long, int)} is odd
   * or equal to 1.
   *
   * @param   pointer1   the memory location to compare against
   * @param   pointer2   the memory location to compare to
   * @param   len     the length of bytes to compare
   * @return  {@code true} if the bytes match, {@code false} if not.
   */
  private static boolean memcmp_b(long pointer1, long pointer2, int len) {
    for (int i = 0; i < len; i++)
      if ((memget_b(pointer1 + i) ^ memget_b(pointer2 + i)) != 0)
        return false;

    return true;
  }

  /**
   * Helper method for {@link #memcmp(long, long, int)}. Compares bytes 2 at a time
   * in the case that {@code len} passed to {@link #memcmp(long, long, int)} is
   * exactly divisible by 2.
   *
   * @param   pointer1   the memory location to compare against
   * @param   pointer2   the memory location to compare to
   * @param   len     the length of bytes to compare
   * @return  {@code true} if the bytes match, {@code false} if not.
   */
  private static boolean memcmp_s(long pointer1, long pointer2, int len) {
    for (int i = 0; i < len; i += 2)
      if ((memget_s(pointer1 + i) ^ memget_s(pointer2 + i)) != 0)
        return false;

    return true;
  }

  /**
   * Helper method for {@link #memcmp(long, long, int)}. Compares bytes 4 at a time
   * in the case that {@code len} passed to {@link #memcmp(long, long, int)} is
   * exactly divisible by 4.
   *
   * @param   pointer1   the memory location to compare against
   * @param   pointer2   the memory location to compare to
   * @param   len     the length of bytes to compare
   * @return  {@code true} if the bytes match, {@code false} if not.
   */
  private static boolean memcmp_i(long pointer1, long pointer2, int len) {
    for (int i = 0; i < len; i += 4)
      if ((memget_i(pointer1 + i) ^ memget_i(pointer2 + i)) != 0)
        return false;

    return true;
  }

  /**
   * Helper method for {@link #memcmp(long, long, int)}. Compares bytes 8 at a time
   * in the case that {@code len} passed to {@link #memcmp(long, long, int)} is
   * exactly divisible by 8.
   *
   * @param   pointer1   the memory location to compare against
   * @param   pointer2   the memory location to compare to
   * @param   len     the length of bytes to compare
   * @return  {@code true} if the bytes match, {@code false} if not.
   */
  private static boolean memcmp_l(long pointer1, long pointer2, int len) {
    for (int i = 0; i < len; i += 8)
      if ((memget_l(pointer1 + i) ^ memget_l(pointer2 + i)) != 0)
        return false;

    return true;
  }

  /**
   * Rounds the number {@code num} to the next multiple of 8.
   *
   * @param   num   the number to round
   * @return  the number rounded to the next multiple of 8
   */
  private static long round(final long num) {
    return (num + 7) / 8 * 8;
  }


  //TODO - Finish documentation for this method
  /**
   * Gets the header size for a class in bytes.
   *
   * <p>64 bit JVM with compressed pointers enabled
   * (JVM flag {@code -XX:+UseCompressedOops}) has a 12
   * byte header and
   *
   * @param   c   the class to get the header size of
   * @return  the header size in bytes of the class
   */
  private static long header_size(Class c) {
    long len;
    if (JVM_64) len = 12;
    else        len = 8;

    if (c.isArray())
      len += 4;

    return len;
  }
}
