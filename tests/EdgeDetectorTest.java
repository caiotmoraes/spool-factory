package br.com.caiot.u1spool;
public class EdgeDetectorTest {
 static void eq(int expected,int actual){if(expected!=actual)throw new AssertionError(expected+" != "+actual);}
 public static void main(String[] args){
  EdgeDetector d=new EdgeDetector();boolean[] empty={false,false,false,false};
  for(int ch=0;ch<4;ch++){
   d.arm(empty,0,180000);eq(-1,d.update(empty,100));
   boolean[] b=empty.clone();b[ch]=true;eq(-1,d.update(b,200));eq(-1,d.update(b,1399));eq(ch,d.update(b,1400));
  }
  d.arm(new boolean[]{true,false,false,false},0,5000);eq(-1,d.update(new boolean[]{true,false,false,false},2000));
  eq(-1,d.update(empty,2100));eq(-1,d.update(new boolean[]{true,false,false,false},2200));eq(0,d.update(new boolean[]{true,false,false,false},3400));
  d.arm(empty,0,5000);eq(-2,d.update(new boolean[]{true,true,false,false},10));
  d.arm(empty,0,5000);eq(-1,d.update(new boolean[]{false,true,false,false},10));eq(-2,d.update(new boolean[]{false,true,true,false},1000));
  d.arm(empty,0,5000);eq(-1,d.update(new boolean[]{true,false,false,false},10));eq(-4,d.update(empty,1300));
  d.arm(empty,0,5000);eq(-3,d.update(empty,5000));
  d.arm(empty,0,5000);d.cancel();eq(-1,d.update(new boolean[]{true,false,false,false},2000));
  d.arm(empty,0,5000);eq(-1,d.update(new boolean[]{true,false,false,false},10));d.arm(empty,20,5000);eq(-1,d.update(empty,1500));
  System.out.println("PASS: 4 feeders, baseline, reinsertion, debounce, simultaneous/sequential ambiguity, removal, expiration, cancellation, replacement.");
 }
}
