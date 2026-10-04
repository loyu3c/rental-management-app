package com.loyu3c.rental;
import org.junit.Test;
import static org.junit.Assert.*;
public class EqualSplitTest {
    @Test public void integerRemaindersPreserveExactTotalAndOrder(){
        assertArrayEquals(new long[]{64,64,64,64},Bookkeeping.equalSplit(256,4));
        assertArrayEquals(new long[]{86,85,85},Bookkeeping.equalSplit(256,3));
        assertArrayEquals(new long[]{1,1,0,0},Bookkeeping.equalSplit(2,4));
        assertArrayEquals(new long[]{256},Bookkeeping.equalSplit(256,1));
        long sum=0;for(long n:Bookkeeping.equalSplit(1000000000L,7))sum+=n;assertEquals(1000000000L,sum);
    }
    @Test public void invalidTotalOrMissingPropertiesDoNotDistribute(){
        for(long n:new long[]{0,-1,1000000001L})try{Bookkeeping.equalSplit(n,4);fail();}catch(IllegalArgumentException expected){}
        try{Bookkeeping.equalSplit(256,0);fail();}catch(IllegalArgumentException expected){}
    }
}
