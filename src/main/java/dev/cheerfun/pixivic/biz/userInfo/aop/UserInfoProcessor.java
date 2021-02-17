package dev.cheerfun.pixivic.biz.userInfo.aop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cheerfun.pixivic.biz.userInfo.dto.ArtistPreViewWithFollowedInfo;
import dev.cheerfun.pixivic.biz.userInfo.dto.IllustrationWithLikeInfo;
import dev.cheerfun.pixivic.biz.web.user.dto.ArtistWithRecentlyIllusts;
import dev.cheerfun.pixivic.common.constant.AuthConstant;
import dev.cheerfun.pixivic.common.constant.RedisKeyConstant;
import dev.cheerfun.pixivic.common.context.AppContext;
import dev.cheerfun.pixivic.common.po.Illustration;
import dev.cheerfun.pixivic.common.po.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2020/2/20 上午11:48
 * @description UserInfoProcessor
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Order(4)
public class UserInfoProcessor {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Pointcut(value = "@annotation(dev.cheerfun.pixivic.biz.userInfo.annotation.WithUserInfo)||@within(dev.cheerfun.pixivic.biz.userInfo.annotation.WithUserInfo)")
    public void pointCut() {
    }

    @AfterReturning(value = "pointCut()", returning = "result")
    public void withUserInfo(Object result) {
        Map<String, Object> context = AppContext.get();
        if (context != null && context.get(AuthConstant.USER_ID) != null) {
            int userId = (int) context.get(AuthConstant.USER_ID);
            if (result instanceof ResponseEntity) {
                deal(result, userId);
            } else if (result instanceof CompletableFuture) {
                ((CompletableFuture) result).thenAccept(e -> {
                    deal(e, userId);
                });
            }
        }
    }

    public void deal(Object responseEntity, Integer userId) {
        Result<List> body = (Result<List>) ((ResponseEntity) responseEntity).getBody();
        List data = body.getData();
        //由于jackson反序列化如果使用泛型则会将对象反序列化为linkedhashmap,这里重新序列化做一个转换,会降低效率
        if (data != null && data.size() > 0) {
/*            if (data.get(0) instanceof Map) {
                body.setData(objectMapper.convertValue(data, new TypeReference<List<Illustration>>() {
                }));
            }*/
            if (data.get(0) instanceof ArtistWithRecentlyIllusts) {
                List<ArtistWithRecentlyIllusts> result = data;
                result.stream().parallel().forEach(r -> {
                    dealIsLikedInfoForIllustList(r.getRecentlyIllustrations(), userId, r.getIsFollowed());
                });
                return;
            }
            dealIsLikedInfoForIllustList(body.getData(), userId, null);

        }
    }

    public List<Illustration> dealIsLikedInfoForIllustList(List<Illustration> illustrationList) {
        Map<String, Object> context = AppContext.get();
        if (context != null && context.get(AuthConstant.USER_ID) != null) {
            int userId = (int) context.get(AuthConstant.USER_ID);
            return dealIsLikedInfoForIllustList(illustrationList, userId, null);
        }
        return illustrationList;

    }

    public List<Collection> dealIsLikedInfoForCollectionList() {
        //处理like信息以及收藏信息
        //获取统计数据
        return null;
    }

    public List<Illustration> dealIsLikedInfoForIllustList(List<Illustration> illustrationList, int userId, Boolean isFollowed) {
        List<Object> isLikedList = new ArrayList<>(illustrationList.size());
        List<Object> isFollowedList = new ArrayList<>(illustrationList.size());
        for (int i = 0; i < illustrationList.size(); i++) {
            isLikedList.add(i, stringRedisTemplate.opsForSet().isMember(RedisKeyConstant.BOOKMARK_REDIS_PRE + userId, String.valueOf(illustrationList.get(i).getId())));
            if (isFollowed != null) {
                isFollowedList.add(isFollowed);
            } else {
                isFollowedList.add(i, stringRedisTemplate.opsForSet().isMember(RedisKeyConstant.ARTIST_FOLLOW_REDIS_PRE + illustrationList.get(i).getArtistId(), String.valueOf(userId)));
            }
        }
        int size = isLikedList.size();
        for (int i = 0; i < size; i++) {
            IllustrationWithLikeInfo illustrationWithLikeInfo = new IllustrationWithLikeInfo(illustrationList.get(i), (Boolean) isLikedList.get(i));
            illustrationWithLikeInfo.setArtistPreView(new ArtistPreViewWithFollowedInfo(illustrationWithLikeInfo.getArtistPreView(), (Boolean) isFollowedList.get(i)));
            illustrationList.set(i, illustrationWithLikeInfo);
        }
        return illustrationList;
    }

}
