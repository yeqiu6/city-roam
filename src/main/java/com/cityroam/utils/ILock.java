package com.cityroam.utils;

public interface ILock {
    boolean tryLock(long timeoutSecond);
    void unlock();
   }
