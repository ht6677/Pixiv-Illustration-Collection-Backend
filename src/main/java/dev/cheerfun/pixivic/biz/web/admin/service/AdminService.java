package dev.cheerfun.pixivic.biz.web.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cheerfun.pixivic.biz.ad.aop.AdvertisementProcessor;
import dev.cheerfun.pixivic.biz.ad.service.AdvertisementService;
import dev.cheerfun.pixivic.biz.web.admin.dto.IllustDTO;
import dev.cheerfun.pixivic.biz.web.admin.mapper.AdminMapper;
import dev.cheerfun.pixivic.biz.web.admin.po.*;
import dev.cheerfun.pixivic.biz.web.admin.repository.*;
import dev.cheerfun.pixivic.biz.web.admin.secmapper.IllustAdminMapper;
import dev.cheerfun.pixivic.biz.web.history.service.IllustHistoryService;
import dev.cheerfun.pixivic.biz.web.illust.service.IllustrationBizService;
import dev.cheerfun.pixivic.common.constant.RedisKeyConstant;
import dev.cheerfun.pixivic.common.po.Illustration;
import dev.cheerfun.pixivic.common.util.translate.service.TranslationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.*;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2020/4/24 2:46 下午
 * @description AdminService
 */
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class AdminService {
    private final StringRedisTemplate stringRedisTemplate;
    private final IllustHistoryService illustHistoryService;
    private final AdminMapper adminMapper;
    private final IllustAdminMapper illustAdminMapper;
    private final CacheManager cacheManager;
    private final TranslationUtil translationUtil;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final DiscussionRepository discussionRepository;
    private final SectionRepository sectionRepository;
    private final CommentRepository commentRepository;
    private final CollectionRepository collectionRepository;
    private final AnnouncementRepository announcementRepository;
    private final AdvertisementRepository advertisementRepository;
    private final AppVersionInfoRepository appVersionInfoRepository;
    private final IllustrationBizService illustrationBizService;
    private final AdvertisementService advertisementService;
    private List<String> keyList;

    @PostConstruct
    public void init() {
        log.info("开始初始化管理员key列表");
        //初始化固定token
        try {
            keyList = adminMapper.queryAllAdminKey();
        } catch (Exception e) {
            log.error("初始化管理员key列表失败");
        }
        log.info("初始化管理员key列表完毕");
    }

    public boolean validateKey(String token) {
        return keyList.contains(token);
    }

    @CacheEvict(value = "illust", key = "#illustDTO.id")
    @Transactional(propagation = Propagation.NOT_SUPPORTED, transactionManager = "SecondaryTransactionManager")
    public void updateIllusts(IllustDTO illustDTO) {
        illustAdminMapper.updateIllusts(illustDTO);
    }

    public Illustration queryIllustrationById(Integer illustId) throws JsonProcessingException {
        Illustration illustration = objectMapper.readValue(objectMapper.writeValueAsString(illustrationBizService.queryIllustrationByIdFromDb(illustId)), Illustration.class);
        String title = translationUtil.translateToChineseByAzureForAdmin(illustration.getTitle());
        if (!"".equals(title)) {
            illustration.setTitle("【" + title + "】" + illustration.getTitle());
        }
        illustration.getTags().forEach(e -> {
            if (e.getTranslatedName() == null || "".equals(e.getTranslatedName())) {
                e.setTranslatedName(translationUtil.translateToChineseByAzureForAdmin(e.getName()));
            }
        });
        /*String caption = translationUtil.translateToChineseByAzureForAdminWithoutCache(Jsoup.parse(illustration.getCaption()).text());
        if (!"".equals(caption)) {
            illustration.setCaption(caption + "|" + illustration.getCaption());
        }*/
        return illustration;
    }

    //画集管理
    public Page<CollectionPO> queryCollection(CollectionPO collectionPO, Integer page, Integer pageSize, String orderBy, String orderByMode) {
        Sort sort = Sort.by(Sort.Direction.fromString(orderByMode), orderBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return collectionRepository.findAll(Example.of(collectionPO), pageable);
    }

    @CacheEvict(value = "collections", key = "#collectionPO.id")
    public CollectionPO updateCollection(CollectionPO collectionPO) {
        return collectionRepository.save(collectionPO);
    }

    @CacheEvict(value = "collections", key = "#collectionId")
    public Boolean deleteCollection(Integer collectionId) {
        collectionRepository.deleteById(collectionId);
        return true;
    }

    //讨论管理
    public Page<DiscussionPO> queryDiscussion(DiscussionPO discussionPO, Integer page, Integer pageSize, String orderBy, String orderByMode) {
        Sort sort = Sort.by(Sort.Direction.fromString(orderByMode), orderBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return discussionRepository.findAll(Example.of(discussionPO), pageable);
    }

    @CacheEvict(value = "discussions", key = "#discussionPO.id")
    public DiscussionPO updateDiscussion(DiscussionPO discussionPO) {
        return discussionRepository.save(discussionPO);
    }

    @CacheEvict(value = "sectionDiscussionCount", key = "#discussionId")
    public Boolean deleteDiscussion(Integer discussionId) {
        discussionRepository.deleteById(discussionId);
        return true;
    }

    //板块管理
    public Page<SectionPO> querySection(SectionPO sectionPO, Integer page, Integer pageSize, String orderBy, String orderByMode) {
        Sort sort = Sort.by(Sort.Direction.fromString(orderByMode), orderBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return sectionRepository.findAll(Example.of(sectionPO), pageable);
    }

    @CacheEvict(value = "section", allEntries = true)
    public SectionPO addSection(SectionPO sectionPO) {
        return sectionRepository.save(sectionPO);
    }

    @CacheEvict(value = "section", allEntries = true)
    public SectionPO updateSection(SectionPO sectionPO) {
        return sectionRepository.save(sectionPO);
    }

    @CacheEvict(value = "section", allEntries = true)
    public Boolean deleteSection(Integer sectionId) {
        sectionRepository.deleteById(sectionId);
        return true;
    }

    //用户管理
    public Page<UserPO> queryUsers(UserPO userPO, Integer page, Integer pageSize, String orderBy, String orderByMode) {
        Sort sort = Sort.by(Sort.Direction.fromString(orderByMode), orderBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return userRepository.findAll(Example.of(userPO), pageable);
    }

    @CacheEvict(value = "users", key = "#userPO.id")
    public UserPO updateUser(UserPO userPO) {
        Optional<UserPO> user = userRepository.findById(userPO.getId());
        if (user.isPresent()) {
            if (userPO.getIsBan() == 0) {
                stringRedisTemplate.opsForSet().add(RedisKeyConstant.ACCOUNT_BAN_SET, String.valueOf(userPO.getId()));
            } else {
                stringRedisTemplate.opsForSet().remove(RedisKeyConstant.ACCOUNT_BAN_SET, String.valueOf(userPO.getId()));
            }
            UserPO result = user.get();
            result.setIsBan(userPO.getIsBan());
            userRepository.save(result);
            return result;
        }
        return null;
    }

    @CacheEvict(value = "users", key = "#userId")
    public Boolean deleteUser(Integer userId) {
        //清理历史记录
        illustHistoryService.deleteByUserId(userId);
        //以下两个不清理 作为协同过滤数据
        //清理画师收藏
        //清理画作收藏

        //清理画集
        //清理讨论
        //清理评论
        //清理推荐
        userRepository.deleteById(userId);
        return true;
    }

    //评论管理
    public Page<CommentPO> queryComment(CommentPO commentPO, Integer page, Integer pageSize, String orderBy, String orderByMode) {
        Sort sort = Sort.by(Sort.Direction.fromString(orderByMode), orderBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return commentRepository.findAll(Example.of(commentPO), pageable);
    }

    @Caching(evict = {
            @CacheEvict(value = "comments", key = "#commentPO.appType+#commentPO.appId"),
            @CacheEvict(value = "topCommentsCount", key = "#commentPO.appType+#commentPO.appId")
    })
    public CommentPO updateComment(CommentPO commentPO) {
        return commentRepository.save(commentPO);
    }

    @Caching(evict = {
            @CacheEvict(value = "comments", key = "#commentPO.appType+#commentPO.appId"),
            @CacheEvict(value = "topCommentsCount", key = "#commentPO.appType+#commentPO.appId")
    })
    public Boolean deleteComment(CommentPO commentPO) {
        commentRepository.deleteById(commentPO.getId());
        return true;
    }

    public CommentPO queryCommentById(Integer commentId) {
        return commentRepository.getOne(commentId);
    }

    //@PostConstruct
    public void test() {
        stringRedisTemplate.opsForValue().set("a", "b");
        System.out.println(stringRedisTemplate.opsForValue().get("a"));
        commentRepository.findAll().forEach(System.out::println);
    }

    @CacheEvict(value = "users", key = "#userId")
    public void banUser(Integer userId) {
        stringRedisTemplate.opsForSet().add(RedisKeyConstant.ACCOUNT_BAN_SET, String.valueOf(userId));
        adminMapper.banUser(userId);
    }

    public Page<AnnouncementPO> queryAnnouncement(AnnouncementPO announcementPO, Integer page, Integer pageSize, String orderBy, String orderByMode) {
        Sort sort = Sort.by(Sort.Direction.fromString(orderByMode), orderBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return announcementRepository.findAll(Example.of(announcementPO), pageable);
    }

    @Caching(evict = {
            @CacheEvict(value = "announcements", key = "#announcementPO.id"),
            @CacheEvict(value = "dailyAnnouncements", allEntries = true),
            @CacheEvict(value = "announcementListCount", allEntries = true),
            @CacheEvict(value = "announcements", allEntries = true)
    })
    public AnnouncementPO updateAnnouncement(AnnouncementPO announcementPO) {
        return announcementRepository.save(announcementPO);
    }

    @Caching(evict = {
            @CacheEvict(value = "dailyAnnouncements", allEntries = true),
            @CacheEvict(value = "announcementListCount", allEntries = true),
            @CacheEvict(value = "announcements", allEntries = true)
    })
    public AnnouncementPO createAnnouncement(AnnouncementPO announcementPO) {
        return announcementRepository.save(announcementPO);
    }

    @Caching(evict = {
            @CacheEvict(value = "announcements", key = "#announcementPO.id"),
            @CacheEvict(value = "dailyAnnouncements", allEntries = true),
            @CacheEvict(value = "announcementListCount", allEntries = true),
            @CacheEvict(value = "announcements", allEntries = true)
    })
    public Boolean deleteAnnouncement(Integer announcementId) {
        announcementRepository.deleteById(announcementId);
        return true;
    }

    public Boolean deleteCache(String region, String key) {
        if (region == null) {
            cacheManager.getCacheNames()
                    .forEach(cacheName -> Objects.requireNonNull(cacheManager.getCache(cacheName)).clear());
        } else {
            if (key == null) {
                cacheManager.getCache(region).clear();
            } else {
                cacheManager.getCache(region).evict(key);
            }
        }
        return true;
    }

    public Page<AdvertisementPO> queryAdvertisements(AdvertisementPO advertisementPO, Integer page, Integer pageSize, String orderBy, String orderByMode) {
        Sort sort = Sort.by(Sort.Direction.fromString(orderByMode), orderBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return advertisementRepository.findAll(Example.of(advertisementPO), pageable);
    }

    public AdvertisementPO updateAdvertisement(AdvertisementPO advertisementPO) {
        AdvertisementPO result = advertisementRepository.save(advertisementPO);
        advertisementService.init();
        return result;
    }

    public AdvertisementPO createAdvertisement(AdvertisementPO advertisementPO) {
        AdvertisementPO result = advertisementRepository.save(advertisementPO);
        advertisementService.init();
        return result;
    }

    public Boolean deleteAdvertisement(Integer adId) {
        advertisementRepository.deleteById(adId);
        advertisementService.init();
        return true;
    }

    public Boolean deleteViewIllustRecommendation() {
        Iterator<RedisClusterNode> iterator = stringRedisTemplate.getConnectionFactory().getClusterConnection().clusterGetNodes().iterator();
        while (iterator.hasNext()) {
            RedisClusterNode clusterNode = iterator.next();
            Set<String> keys = stringRedisTemplate.opsForCluster().keys(clusterNode, RedisKeyConstant.USER_RECOMMEND_VIEW_ILLUST + "*");
            stringRedisTemplate.unlink(keys);
        }
        return true;
    }

    @Transactional
    public void blockIllustrationById(List<Integer> illustIdList) {
        for (Integer illustId : illustIdList) {
            blockIllustrationById(illustId);
        }
    }

    @CacheEvict(value = "illust", key = "#illustId")
    public void blockIllustrationById(Integer illustId) {
        if (adminMapper.blockIllustrationById(illustId) == 1) {
            stringRedisTemplate.opsForSet().add(RedisKeyConstant.BLOCK_ILLUSTS_SET, String.valueOf(illustId));
        }
    }

    public List<Integer> queryBlockIllust(Integer illustId) {
        if (illustId != null) {
            return adminMapper.queryBlockIllustById(illustId);
        }
        return adminMapper.queryBlockIllust();
    }

    @Transactional
    @CacheEvict(value = "illust", key = "#illustId")
    public void removeIllustFromBlockIllust(Integer illustId) {
        if (adminMapper.removeIllustFromBlockIllust(illustId) == 1) {
            stringRedisTemplate.opsForSet().remove(RedisKeyConstant.BLOCK_ILLUSTS_SET, String.valueOf(illustId));
        }
    }

    public void blockArtistById(List<Integer> artistIdList) {
        for (Integer artistId : artistIdList) {
            blockArtistById(artistId);
        }
    }

    @Transactional
    @CacheEvict(value = "artist", key = "#artistId")
    public void blockArtistById(Integer artistId) {
        if (adminMapper.blockArtistById(artistId) == 1) {
            stringRedisTemplate.opsForSet().add(RedisKeyConstant.BLOCK_ARTISTS_SET, String.valueOf(artistId));
            //查找出画师的所有画作 都加入屏蔽列表
            blockIllustrationById(queryIllustrationsByArtistId(artistId));
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED, transactionManager = "SecondaryTransactionManager")
    public List<Integer> queryIllustrationsByArtistId(Integer artistId) {
        return illustAdminMapper.queryIllustrationsByArtistId(artistId);
    }

    public List<Integer> queryBlockArtist(Integer artistId) {
        if (artistId != null) {
            return adminMapper.queryBlockArtistById(artistId);
        }
        return adminMapper.queryBlockArtist();

    }

    @CacheEvict(value = "artist", key = "#artistId")
    public void removeArtistFromBlockArtist(Integer artistId) {
        if (adminMapper.removeArtistFromBlockArtist(artistId) == 1) {
            stringRedisTemplate.opsForSet().remove(RedisKeyConstant.BLOCK_ARTISTS_SET, String.valueOf(artistId));
            List<Integer> illustIdList = queryIllustrationsByArtistId(artistId);
            for (Integer illustId : illustIdList) {
                removeIllustFromBlockIllust(illustId);
            }
        }
    }

    public Page<AppVersionInfoPO> queryAppVersionInfo(AppVersionInfoPO appVersionInfoPO, Integer page, Integer pageSize, String orderBy, String orderByMode) {
        Sort sort = Sort.by(Sort.Direction.fromString(orderByMode), orderBy);
        Pageable pageable = PageRequest.of(page - 1, pageSize, sort);
        return appVersionInfoRepository.findAll(Example.of(appVersionInfoPO), pageable);
    }

    @Caching(evict = {
            @CacheEvict(value = "latestAppVersion", allEntries = true),
            @CacheEvict(value = "appVersionList", allEntries = true),
            @CacheEvict(value = "appVersionCount", allEntries = true)
    })
    public AppVersionInfoPO updateAppVersionInfo(AppVersionInfoPO appVersionInfoPO) {
        return appVersionInfoRepository.save(appVersionInfoPO);
    }

    @Caching(evict = {
            @CacheEvict(value = "latestAppVersion", allEntries = true),
            @CacheEvict(value = "appVersionList", allEntries = true),
            @CacheEvict(value = "appVersionCount", allEntries = true)
    })
    public AppVersionInfoPO createAppVersionInfo(AppVersionInfoPO appVersionInfoPO) {
        return appVersionInfoRepository.save(appVersionInfoPO);
    }

    @Caching(evict = {
            @CacheEvict(value = "latestAppVersion", allEntries = true),
            @CacheEvict(value = "appVersionList", allEntries = true),
            @CacheEvict(value = "appVersionCount", allEntries = true)
    })
    public Boolean deleteAppVersionInfo(Integer appVersionInfoId) {
        appVersionInfoRepository.deleteById(appVersionInfoId);
        return true;
    }

    public Boolean updateAdvertisementList() {
        advertisementService.init();
        return true;
    }
}
