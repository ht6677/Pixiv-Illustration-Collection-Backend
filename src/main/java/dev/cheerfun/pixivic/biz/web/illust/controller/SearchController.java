package dev.cheerfun.pixivic.biz.web.illust.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.cheerfun.pixivic.basic.auth.annotation.PermissionRequired;
import dev.cheerfun.pixivic.basic.auth.constant.PermissionLevel;
import dev.cheerfun.pixivic.basic.ratelimit.annotation.RateLimit;
import dev.cheerfun.pixivic.basic.sensitive.annotation.SensitiveCheck;
import dev.cheerfun.pixivic.biz.ad.annotation.WithAdvertisement;
import dev.cheerfun.pixivic.biz.analysis.tag.po.TrendingTags;
import dev.cheerfun.pixivic.biz.analysis.tag.service.TrendingTagsService;
import dev.cheerfun.pixivic.biz.userInfo.annotation.WithUserInfo;
import dev.cheerfun.pixivic.biz.web.illust.domain.SearchSuggestion;
import dev.cheerfun.pixivic.biz.web.illust.domain.response.PixivSearchCandidatesResponse;
import dev.cheerfun.pixivic.biz.web.illust.service.IllustrationBizService;
import dev.cheerfun.pixivic.biz.web.illust.service.SearchService;
import dev.cheerfun.pixivic.common.po.Illustration;
import dev.cheerfun.pixivic.common.po.Result;
import dev.cheerfun.pixivic.common.util.translate.service.TranslationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/08/14 22:45
 * @description SearchController
 */
@RestController
@Validated
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SearchController {
    private final SearchService searchService;
    private final IllustrationBizService illustrationBizService;
    private final TranslationUtil translationUtil;
    private final TrendingTagsService trendingTagsService;

    @GetMapping("/trendingTags")
    public ResponseEntity<Result<List<TrendingTags>>> getTrendingTags() throws JsonProcessingException {
        return ResponseEntity.ok().body(new Result<>("搜索热门标签成功", trendingTagsService.queryByDate(LocalDate.now().toString())));
    }

    @GetMapping("/keywords/**/candidates")
    public CompletableFuture<ResponseEntity<Result<PixivSearchCandidatesResponse>>> getCandidateWords(HttpServletRequest request) {
        return searchService.getCandidateWords(searchService.getKeyword(request)).thenApply(r -> ResponseEntity.ok().body(new Result<>("搜索候选词获取成功", r)));
    }

    @GetMapping("/keywords/**/suggestions")
    public CompletableFuture<ResponseEntity<Result<List<SearchSuggestion>>>> getSearchSuggestion(HttpServletRequest request) {
        return searchService.getSearchSuggestion(searchService.getKeyword(request)).thenApply(r -> ResponseEntity.ok().body(new Result<>("搜索建议获取成功", r)));
    }

    @GetMapping("/keywords/**/pixivSuggestions")
    public CompletableFuture<ResponseEntity<Result<List<SearchSuggestion>>>> getPixivSearchSuggestion(HttpServletRequest request) {
        return searchService.getPixivSearchSuggestion(searchService.getKeyword(request)).thenApply(r -> ResponseEntity.ok().body(new Result<>("搜索建议(来自Pixiv)获取成功", r)));
    }

    @GetMapping("/keywords/**/translations")
    public ResponseEntity<Result<SearchSuggestion>> getKeywordTranslation(HttpServletRequest request) {
        return ResponseEntity.ok().body(new Result<>("搜索词翻译获取成功", searchService.getKeywordTranslation(searchService.getKeyword(request))));
    }

    @GetMapping("/illustrations")
    @WithUserInfo
    @WithAdvertisement
    @PermissionRequired(PermissionLevel.ANONYMOUS)
    @RateLimit
    public CompletableFuture<ResponseEntity<Result<List<Illustration>>>> searchByKeyword(
            @SensitiveCheck
            @RequestParam
            @NotBlank
                    String keyword,
            @RequestParam(defaultValue = "30")
            @Max(30) @Min(1)
                    int pageSize,
            @RequestParam
            @Max(330) @Min(1)
                    int page,
            @RequestParam(defaultValue = "original")
                    String searchType,//搜索类型（原生、自动翻译、自动匹配词条）
            @RequestParam(defaultValue = "illust")
                    String illustType,
            @RequestParam(required = false)
                    Integer minWidth,
            @RequestParam(required = false)
                    Integer minHeight,
            @RequestParam(required = false)
                    String beginDate,
            @RequestParam(required = false)
                    String endDate,
            @RequestParam(defaultValue = "0")
                    Integer xRestrict,
            @RequestParam(required = false)
                    Integer popWeight,
            @RequestParam(required = false)
                    Integer minTotalBookmarks,
            @RequestParam(required = false)
                    Integer minTotalView,
            @RequestParam(defaultValue = "5")
                    Integer maxSanityLevel, @RequestHeader(value = "Authorization", required = false) String token) {
        if ("autoTranslate".equals(searchType)) {
            //自动翻译
            String[] keywords = keyword.split("\\|\\|");
            keyword = Arrays.stream(keywords).map(translationUtil::translateToChineseByAzure).reduce((s1, s2) -> s1 + " " + s2).get();
        }
        CompletableFuture<List<Illustration>> searchResultCompletableFuture = searchService.searchByKeyword(keyword, pageSize, page, searchType, illustType, minWidth, minHeight, beginDate, endDate, xRestrict, popWeight, minTotalBookmarks, minTotalView, maxSanityLevel, null);
        return searchResultCompletableFuture.thenApply(illustrations -> ResponseEntity.ok().body(new Result<>("搜索结果获取成功", illustrations)));
    }

    @GetMapping("/similarityImages")
    public CompletableFuture<ResponseEntity<Result<List<Illustration>>>> searchByImage(@RequestParam String imageUrl) {
        return searchService.searchByImage(imageUrl).thenApply(saucenaoResponse -> ResponseEntity.ok().body(new Result<>("搜索结果获取成功", saucenaoResponse)));

    }

}
