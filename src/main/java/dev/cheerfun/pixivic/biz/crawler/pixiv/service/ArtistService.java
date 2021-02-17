package dev.cheerfun.pixivic.biz.crawler.pixiv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cheerfun.pixivic.biz.crawler.pixiv.dto.ArtistDTO;
import dev.cheerfun.pixivic.biz.crawler.pixiv.dto.IllustrationDTO;
import dev.cheerfun.pixivic.biz.crawler.pixiv.dto.IllustsDTO;
import dev.cheerfun.pixivic.biz.crawler.pixiv.secmapper.ArtistMapper;
import dev.cheerfun.pixivic.common.po.Artist;
import dev.cheerfun.pixivic.common.po.Illustration;
import dev.cheerfun.pixivic.common.util.pixiv.RequestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/08/10 21:22
 * @description ArtistService
 */
@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ArtistService {
    private final RequestUtil requestUtil;
    private final ObjectMapper objectMapper;
    private final ArtistMapper artistMapper;
    private final IllustrationService illustrationService;
    private final StringRedisTemplate stringRedisTemplate;
    private ReentrantLock lock = new ReentrantLock();

    private List<Integer> waitForReDownload = new ArrayList<>();

    public Artist pullArtistsInfo(Integer artistId) {
        List<Integer> artistIds = Collections.singletonList(artistId);
        List<Artist> artists = pullArtistsInfo(artistIds);
        if (artists != null && artists.size() > 0)
            return artists.get(0);
        return null;
    }

    public void pullArtistAllIllust(Integer artistId) {
        log.info("开始全量爬取画师：" + artistId + "的画作");
        boolean flag = true;
        int offset = 0;
        while (flag) {
            log.info("开始抓取画师：" + artistId + "的第" + offset + "页作品");
            try {
                IllustsDTO illustrationDetailDTO = (IllustsDTO) requestUtil.getJsonSync("http://proxy.pixivic.com:23334/v1/user/illusts?user_id=" + artistId + "&offset=" + offset * 30, IllustsDTO.class);
                if (illustrationDetailDTO != null && illustrationDetailDTO.getIllusts() != null && illustrationDetailDTO.getIllusts().size() > 0) {
                    illustrationService.saveToDb(illustrationDetailDTO.getIllusts().stream().map(IllustrationDTO::castToIllustration).collect(Collectors.toList()));
                    log.info("抓取画师：" + artistId + "的第" + offset + "页作品成功");
                    offset++;
                } else {
                    flag = false;
                    log.info("画师：" + artistId + "共有" + offset + "页作品成功");
                    if (offset > 0) {
                        updateArtistSummary(artistId);
                    }
                }
            } catch (Exception exception) {
                flag = false;
                log.error("抓取画师：" + artistId + "的第" + offset + "页作品失败");
            }
        }

    }

    public void pullArtistIllustList() throws IOException, InterruptedException {
        //初始化,查看记录
        List<String> strings = Files.readAllLines(Paths.get("/home/PIC/artistId.txt"));
        String artist = stringRedisTemplate.opsForValue().get("artist");
        String[] split = artist.split(":");
        String artistIndex = split[0];
        int offset = 0;
        //开始抓取
        for (int i = Integer.parseInt(artistIndex); i < strings.size(); i++) {
            String s = strings.get(i);
            boolean flag = true;
            //持久化到本地
            while (flag) {
                System.out.println("开始抓取第" + i + "个画师(id:" + s + ")的第" + offset + "作品");
                IllustsDTO illustrationDetailDTO = (IllustsDTO) requestUtil.getJsonSync("https://app-api.pixiv.net/v1/user/illusts?user_id=" + s + "&offset=" + offset, IllustsDTO.class);
                Files.write(Paths.get("/home/artist/" + s + "-" + offset + ".json"), objectMapper.writeValueAsString(illustrationDetailDTO).getBytes());
                if (illustrationDetailDTO.getNextUrl() == null) {
                    flag = false;
                    offset = 0;
                    stringRedisTemplate.opsForValue().set("artist", String.valueOf(i));
                } else {
                    stringRedisTemplate.opsForValue().set("artist", String.valueOf(i));
                    offset += 30;
                }
                Thread.sleep(500);
            }

        }

    }

    public void pullArtistLatestIllust(Integer artistId, String type) throws IOException {
        IllustsDTO illustrationDetailDTOPage1 = (IllustsDTO) requestUtil.getJsonSync("http://proxy.pixivic.com:23334/v1/user/illusts?user_id=" + artistId + "&offset=0&type=" + type, IllustsDTO.class);
        // IllustsDTO illustrationDetailDTOPage2 = (IllustsDTO) requestUtil.getJsonSync("http://proxy.pixivic.com:23334/v1/user/illusts?user_id=" + artistId + "&offset=30&type=" + type, IllustsDTO.class);
        //IllustsDTO illustrationDetailDTOPage1 = pixivService.pullArtistIllust(artistId, 0, type).execute().body();
        //   System.out.println(illustrationDetailDTOPage1);
        if (illustrationDetailDTOPage1 != null && illustrationDetailDTOPage1.getIllusts() != null) {
            List<Illustration> illustrationListPage1 = illustrationDetailDTOPage1.getIllusts().stream().filter(Objects::nonNull).map(IllustrationDTO::castToIllustration).collect(Collectors.toList());
            if (illustrationListPage1.size() > 0) {
                illustrationService.saveToDb(illustrationListPage1);
            }
        }
    }


    public List<Artist> pullArtistsInfo(List<Integer> artistIds) {
        List<Integer> artistIdsToDownload = artistMapper.queryArtistsNotInDb(artistIds);
        List<Artist> artistList = artistIdsToDownload.stream().parallel().distinct().map(i -> {
            try {
                CompletableFuture<Artist> artistCompletableFuture = requestUtil.getJson("http://proxy.pixivic.com:23334/v1/user/detail?user_id=" + i + "&filter=for_ios")
                        .thenApply(result -> {
                            if ("false".equals(result)) {
                                this.addToWaitingList(i);
                                return null;
                            }
                            Artist artist = null;
                            try {
                                artist = ArtistDTO.castToArtist(objectMapper.readValue(result, new TypeReference<ArtistDTO>() {
                                }));
                            } catch (IOException e) {
                                return null;
                            }
                            return artist;
                        });
                return artistCompletableFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("抓取画师信息错误" + e.getMessage());
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
        if (artistList.size() > 0) {
            log.info("画师信息为");
            artistList.forEach(System.out::println);
            artistMapper.insert(artistList);
            updateArtistSummary(artistIds);
            System.out.println("画师信息入库完毕");
        }
        return artistList;
    }

    private void updateArtistSummary(List<Integer> artistIdList) {
        artistIdList.forEach(this::updateArtistSummary);
    }

    @CacheEvict(value = "artistSummarys", key = "#artistId")
    public void updateArtistSummary(Integer artistId) {
        artistMapper.updateArtistSummary(artistId);
    }

    private void dealReDownload() throws InterruptedException {
        final CountDownLatch cd = new CountDownLatch(waitForReDownload.size());
        waitForReDownload.forEach(i -> requestUtil.getJson("http://proxy.pixivic.com:23334/v1/user/detail?user_id=" + i + "&filter=for_ios").thenAccept(s -> cd.countDown()));
        cd.await(waitForReDownload.size() * 11, TimeUnit.SECONDS);
    }

    private void addToWaitingList(int id) {
        lock.lock();
        try {
            waitForReDownload.add(id);
        } finally {
            lock.unlock();
        }
    }
}
