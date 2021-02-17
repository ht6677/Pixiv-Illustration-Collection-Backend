package dev.cheerfun.pixivic.biz.web.vip.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface VIPMapper {
    @Insert("insert into vip_exchange_codes(code_id, type, create_date) values (#{sn},#{type},#{now})")
    void inertExchangeCode(int sn, byte type, String now);

    @Update("update vip_exchange_codes set use_flag=1,user_id=#{userId} where code_id =#{codeId} and use_flag=0")
    Integer updateExchangeCode(int codeId, int userId);

    @Select("select user_vip_activity_log_id from user_vip_activity_log where user_id=#{userId} and activity_name =#{activityName}")
    Integer checkActivity(Integer userId, String activityName);

    @Insert("insert into user_vip_activity_log(user_id, activity_name, create_time) values (#{userId},#{activityName},now())")
    void addParticipateActivityLog(Integer userId, String activityName);
}
