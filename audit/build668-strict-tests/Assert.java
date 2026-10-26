package org.junit;
public class Assert {
 public static void assertTrue(boolean ok){if(!ok)throw new AssertionError("expected true");}
 public static void assertFalse(boolean ok){if(ok)throw new AssertionError("expected false");}
 public static void assertNull(Object o){if(o!=null)throw new AssertionError("expected null, was: "+o);}
 public static void assertEquals(Object a,Object b){if(a==null?b!=null:!a.equals(b))throw new AssertionError("expected "+a+" got "+b);}
 public static void assertEquals(double a,double b,double delta){if(Math.abs(a-b)>delta)throw new AssertionError("expected "+a+" got "+b);}
}
