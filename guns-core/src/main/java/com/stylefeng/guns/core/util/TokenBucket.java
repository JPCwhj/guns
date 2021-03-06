package com.stylefeng.guns.core.util;

/*
令牌桶算法(限流)
 */
public class TokenBucket {

    private int bucketNum = 100;    //桶容量
    private int rate = 1;               //流入速度
    private int nowTokens;          //当前令牌数量
    private long timeStamp = getNowTime();         //时间

    private long getNowTime(){
        return System.currentTimeMillis();
    }

    private int min(int tokens){
        if(tokens > bucketNum){
            return bucketNum;
        }
        return tokens;
    }

    public boolean getToken() {
        // 记录来拿令牌的时间
        long nowTime = getNowTime();
        // 添加令牌【判断该有多少个令牌】
        nowTokens = (int) (nowTokens + ((nowTime - timeStamp)*rate));
        // 添加以后的令牌数量与桶的容量那个小
        nowTokens = min(nowTokens);
        System.out.println("当前令牌数量:" + nowTokens);
        // 修改拿令牌的时间
        timeStamp = nowTime;
        // 判断令牌是否足够
        if(nowTokens >= 1){
            nowTokens --;
            return true;
        }
        return false;
    }
}
