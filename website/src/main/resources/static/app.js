const { createApp } = Vue;

const categoryMap = {
    "传统技艺": "Traditional Craftsmanship",
    "传统戏曲": "Traditional Opera",
    "古建筑": "Historic Architecture",
    "民俗节庆": "Folk Rituals & Festivals",
    "其他": "Others"
};

const nicknameMap = {
    "平台管理员": "Platform Curator",
    "小组成员1": "Team Member 1"
};

const textMap = {
    "苏绣的针法之美": "The Needlework Elegance of Suzhou Embroidery",
    "苏绣是中国传统刺绣的重要代表，讲究针法细腻、设色雅致。": "Suzhou embroidery is one of the most celebrated traditions of Chinese needlework, known for refined stitching and graceful colour composition.",
    "这篇文章适合后面再补几张针法细节图。": "This article would become even stronger with a few close-up images of the stitching techniques.",
    "手工艺、织染、雕刻等": "Craft traditions, dyeing, weaving, carving, and studio practice.",
    "戏曲、曲艺、说唱艺术": "Opera, ballad performance, oral storytelling, and stage heritage.",
    "古城、古桥、古园林与历史建筑": "Ancient towns, bridges, gardens, and protected architecture.",
    "节日仪式、民间风俗与庆典": "Festival ritual, seasonal custom, and collective celebration."
};

const heritageMap = {
    "苏绣": "Suzhou Embroidery",
    "昆曲": "Kunqu Opera",
    "蓝染技艺": "Indigo Dyeing",
    "皮影戏": "Shadow Puppetry",
    "元宵灯会": "Lantern Festival Procession",
    "廊桥营造技艺": "Covered Bridge Carpentry",
    "泥塑": "Clay Figurine Sculpture"
};

const regionMap = {
    "江苏苏州": "Suzhou, Jiangsu",
    "陕西西安": "Xi'an, Shaanxi",
    "福建泉州": "Quanzhou, Fujian",
    "贵州黔东南": "Qiandongnan, Guizhou",
    "浙江温州": "Wenzhou, Zhejiang",
    "天津": "Tianjin"
};

const postTitleByteLimit = 150;
const postContentByteLimit = 60000;
const requiredSecurityQuestionCount = 3;
const siteLanguageStorageKey = "publication-studio-language";

const readStoredSiteLanguage = () => localStorage.getItem(siteLanguageStorageKey) === "en" ? "en" : "zh";

const createEmptySecurityQuestions = () => Array.from({ length: requiredSecurityQuestionCount }, (_, index) => ({
    questionOrder: index + 1,
    questionText: "",
    answer: ""
}));

const createEmptySecurityAnswers = () => Array.from({ length: requiredSecurityQuestionCount }, () => "");

const createEmptyRegisterForm = () => ({
    username: "",
    nickname: "",
    email: "",
    phone: "",
    password: "",
    confirmPassword: "",
    securityQuestions: createEmptySecurityQuestions()
});

const backendMessageMap = {};

const categoryDescriptionMap = {
    "传统技艺": "Material culture, workshop practice, motifs, and making processes.",
    "传统戏曲": "Performance traditions, costume, vocal lineages, and stage memory.",
    "古建筑": "Historic buildings, construction craft, spatial heritage, and preservation.",
    "民俗节庆": "Seasonal ritual, festivals, community gatherings, and oral custom.",
    "其他": "Other heritage materials that do not fit the main collections."
};

const defaultCoverFallbackImage = "/images/default-covers/default-heritage.svg";

const defaultCoverImageMap = {
    "traditional craftsmanship": "/images/default-covers/default-craftsmanship.jpg",
    "traditional opera": "/images/default-covers/default-opera.webp",
    "historic architecture": "/images/default-covers/default-architecture.jpg",
    "folk rituals & festivals": "/images/default-covers/default-folk-rituals.jpg"
};

const categoryPieColors = ["#8f4b2f", "#6d7561", "#c9a36d", "#2f241d", "#57707a"];

const fallbackCategories = [
    { id: 1, name: "传统技艺", description: "Material culture, workshop practice, motifs, and making processes." },
    { id: 2, name: "传统戏曲", description: "Performance traditions, costume, vocal lineages, and stage memory." },
    { id: 3, name: "古建筑", description: "Historic buildings, construction craft, spatial heritage, and preservation." },
    { id: 4, name: "民俗节庆", description: "Seasonal ritual, festivals, community gatherings, and oral custom." },
    { id: 5, name: "其他", description: "Other heritage materials that do not fit the main collections." }
];

const fallbackPostDetails = {
    101: {
        id: 101,
        title: "The Garden Stage Language of Kunqu",
        content: "This front-end demo article shows how a longer heritage essay can be displayed on the platform. It combines historical background, performance detail, and scene-setting notes that a student team can later connect to real backend data.",
        coverImageUrl: "https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=1200&q=80",
        heritageName: "昆曲",
        region: "江苏苏州",
        status: "PUBLISHED",
        authorName: "平台管理员",
        categoryName: "传统戏曲",
        likeCount: 34,
        favoriteCount: 19,
        commentCount: 6,
        imageUrls: [
            "https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=1200&q=80",
            "https://images.unsplash.com/photo-1518998053901-5348d3961a04?auto=format&fit=crop&w=1200&q=80"
        ],
        comments: [
            { id: 1, authorNickname: "平台管理员", content: "A useful angle here would be to add costume symbolism and hand gesture notes.", createdAt: "2026-04-05T09:30:00" },
            { id: 2, authorNickname: "小组成员1", content: "The scene description already feels like a museum interpretation panel.", createdAt: "2026-04-05T14:10:00" }
        ],
        createdAt: "2026-04-05T08:00:00"
    },
    102: {
        id: 102,
        title: "Reading the Timber Logic of Historic Covered Bridges",
        content: "A category detail page should be able to surface architecture essays like this one, where structural systems, repair history, and local materials are described visually and accessibly.",
        coverImageUrl: "https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=1200&q=80",
        heritageName: "廊桥营造技艺",
        region: "浙江温州",
        status: "PUBLISHED",
        authorName: "平台管理员",
        categoryName: "古建筑",
        likeCount: 27,
        favoriteCount: 12,
        commentCount: 4,
        imageUrls: [
            "https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=1200&q=80",
            "https://images.unsplash.com/photo-1529421306624-54a49f2d50f8?auto=format&fit=crop&w=1200&q=80"
        ],
        comments: [
            { id: 3, authorNickname: "小组成员1", content: "The bridge section drawings would fit nicely into the gallery area.", createdAt: "2026-04-06T10:05:00" }
        ],
        createdAt: "2026-04-04T11:20:00"
    },
    103: {
        id: 103,
        title: "Field Notes from an Indigo Dye Workshop",
        content: "This sample entry demonstrates how process photography, tool documentation, and oral explanation can live together in one article page without needing video playback.",
        coverImageUrl: "https://images.unsplash.com/photo-1517697471339-4aa32003c11a?auto=format&fit=crop&w=1200&q=80",
        heritageName: "蓝染技艺",
        region: "贵州黔东南",
        status: "PUBLISHED",
        authorName: "小组成员1",
        categoryName: "传统技艺",
        likeCount: 41,
        favoriteCount: 22,
        commentCount: 9,
        imageUrls: [
            "https://images.unsplash.com/photo-1517697471339-4aa32003c11a?auto=format&fit=crop&w=1200&q=80",
            "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1200&q=80"
        ],
        comments: [
            { id: 4, authorNickname: "平台管理员", content: "This one is strong enough to appear in the homepage ranking module.", createdAt: "2026-04-07T15:20:00" }
        ],
        createdAt: "2026-04-03T16:40:00"
    },
    104: {
        id: 104,
        title: "Lantern Processions and Night Ritual in the Old City",
        content: "Festival-oriented content can sit in the homepage alongside craft and architecture posts, giving the archive a more social and seasonal rhythm.",
        coverImageUrl: "https://images.unsplash.com/photo-1513151233558-d860c5398176?auto=format&fit=crop&w=1200&q=80",
        heritageName: "元宵灯会",
        region: "福建泉州",
        status: "PUBLISHED",
        authorName: "小组成员1",
        categoryName: "民俗节庆",
        likeCount: 18,
        favoriteCount: 9,
        commentCount: 3,
        imageUrls: [
            "https://images.unsplash.com/photo-1513151233558-d860c5398176?auto=format&fit=crop&w=1200&q=80"
        ],
        comments: [],
        createdAt: "2026-04-02T18:15:00"
    },
    105: {
        id: 105,
        title: "Clay Figurine Colour Layers in Studio Practice",
        content: "This placeholder article supports the personal profile and category views by giving the platform a fuller archive for presentation and navigation testing.",
        coverImageUrl: "https://images.unsplash.com/photo-1460661419201-fd4cecdf8a8b?auto=format&fit=crop&w=1200&q=80",
        heritageName: "泥塑",
        region: "天津",
        status: "PUBLISHED",
        authorName: "平台管理员",
        categoryName: "传统技艺",
        likeCount: 16,
        favoriteCount: 8,
        commentCount: 2,
        imageUrls: [
            "https://images.unsplash.com/photo-1460661419201-fd4cecdf8a8b?auto=format&fit=crop&w=1200&q=80"
        ],
        comments: [],
        createdAt: "2026-04-01T13:00:00"
    },
    106: {
        id: 106,
        title: "Pattern Fragments from Shadow Puppet Costuming",
        content: "A richer front page works better when different article types can coexist: research notes, object studies, visual records, and curatorial reflections.",
        coverImageUrl: "https://images.unsplash.com/photo-1500534623283-312aade485b7?auto=format&fit=crop&w=1200&q=80",
        heritageName: "皮影戏",
        region: "陕西西安",
        status: "PUBLISHED",
        authorName: "平台管理员",
        categoryName: "传统戏曲",
        likeCount: 22,
        favoriteCount: 11,
        commentCount: 5,
        imageUrls: [
            "https://images.unsplash.com/photo-1500534623283-312aade485b7?auto=format&fit=crop&w=1200&q=80"
        ],
        comments: [],
        createdAt: "2026-03-30T09:00:00"
    }
};

const workspaceDraftTemplates = [
    {
        id: "draft-1",
        title: "Draft: Mapping Ritual Sound in Temple Courtyards",
        categoryName: "民俗节庆",
        updatedAt: "2026-04-10T19:30:00",
        note: "Waiting for audio transcript cleanup and one cover image."
    },
    {
        id: "draft-2",
        title: "Draft: Motif Variations in Rural Woodcarving",
        categoryName: "传统技艺",
        updatedAt: "2026-04-09T16:10:00",
        note: "Needs a stronger opening paragraph and workshop credits."
    }
];

const workspacePendingTemplates = [
    {
        id: "pending-1",
        title: "Pending Review: Courtyard Festival Route Documentation",
        categoryName: "民俗节庆",
        updatedAt: "2026-04-08T12:40:00",
        note: "Submitted to the editorial queue. Waiting for category approval."
    },
    {
        id: "pending-2",
        title: "Pending Review: Roof Ornament Sketches from the South Gate",
        categoryName: "古建筑",
        updatedAt: "2026-04-07T10:25:00",
        note: "Awaiting image quality check and curator comments."
    }
];

const permissionRequestTemplates = [
    {
        id: "request-1",
        title: "Curator Review Access",
        status: "APPROVED",
        createdAt: "2026-04-03T09:00:00",
        note: "Approved for collection curation preview and metadata editing."
    },
    {
        id: "request-2",
        title: "Extended Publishing Rights",
        status: "PENDING",
        createdAt: "2026-04-10T14:45:00",
        note: "Pending faculty review."
    }
];

const adminApprovalTemplates = [
    {
        id: "approval-1",
        title: "Permission Request: Regional Editor",
        applicant: "Archive Visitor",
        type: "Role application",
        status: "Pending",
        submittedAt: "2026-04-11T09:25:00"
    },
    {
        id: "approval-2",
        title: "Publication Review: Metadata Completeness",
        applicant: "Team Member 1",
        type: "Publication review",
        status: "Pending",
        submittedAt: "2026-04-11T15:40:00"
    }
];

const clone = (value) => JSON.parse(JSON.stringify(value));

const createEmptyPostForm = () => ({
    participantId: "",
    title: "",
    content: "",
    categoryId: "",
    coverImageUrl: "",
    heritageName: "",
    region: "",
    imageUrls: [],
    publication: true,
    publicationAuthors: "",
    publicationYear: "",
    venue: "",
    abstractText: "",
    keywords: "",
    doi: "",
    bibtex: "",
    researchArea: "",
    pdfUrl: "",
    codeUrl: "",
    datasetUrl: ""
});

const draftCacheKey = "heritage-draft-cache";
const likedPostsStorageKey = "heritage-liked-posts";

const readStoredDraftCache = () => {
    const raw = localStorage.getItem(draftCacheKey);
    if (!raw) {
        return {};
    }

    try {
        return JSON.parse(raw);
    } catch (error) {
        localStorage.removeItem(draftCacheKey);
        return {};
    }
};

const readStoredUser = () => {
    const raw = localStorage.getItem("heritage-current-user");
    if (!raw) {
        return null;
    }

    try {
        return JSON.parse(raw);
    } catch (error) {
        localStorage.removeItem("heritage-current-user");
        return null;
    }
};

const readStoredLikedPostMap = () => {
    const raw = localStorage.getItem(likedPostsStorageKey);
    if (!raw) {
        return {};
    }

    try {
        return JSON.parse(raw);
    } catch (error) {
        localStorage.removeItem(likedPostsStorageKey);
        return {};
    }
};

const resolveImageCandidate = (candidate) => {
    if (!candidate) {
        return "";
    }
    if (typeof candidate === "string") {
        return candidate.trim();
    }
    if (typeof candidate === "object") {
        return String(
            candidate.coverImageUrl
            || candidate.imageUrl
            || candidate.url
            || candidate.src
            || candidate.path
            || ""
        ).trim();
    }
    return "";
};

const getPostCategoryName = (post) => {
    const category = post?.category ?? post?.categoryName ?? post?.collectionName ?? "";
    if (typeof category === "string") {
        return category;
    }
    if (category && typeof category === "object") {
        return category.name || category.categoryName || category.title || category.label || "";
    }
    return "";
};

const normaliseCategoryKey = (categoryName) => {
    const rawName = String(categoryName || "").trim();
    const displayName = categoryMap[rawName] || rawName;
    return displayName
        .toLowerCase()
        .replace(/\s*&\s*/g, " & ")
        .replace(/\s+/g, " ")
        .trim();
};

const resolveDefaultCoverImage = (categoryName) =>
    defaultCoverImageMap[normaliseCategoryKey(categoryName)] || defaultCoverFallbackImage;

const resolvePostOwnCoverImage = (post) => {
    const directCover = [
        post?.coverImageUrl,
        post?.coverImage,
        post?.imageUrl,
        post?.mainImageUrl,
        post?.thumbnailUrl
    ].map(resolveImageCandidate).find(Boolean);

    if (directCover) {
        return directCover;
    }

    const imageCollections = [post?.imageUrls, post?.images, post?.media, post?.galleryImages];
    for (const collection of imageCollections) {
        if (!Array.isArray(collection)) {
            continue;
        }
        const firstImage = collection.map(resolveImageCandidate).find(Boolean);
        if (firstImage) {
            return firstImage;
        }
    }

    return "";
};

const resolvePostCoverImage = (post) =>
    resolvePostOwnCoverImage(post) || resolveDefaultCoverImage(getPostCategoryName(post));

const normaliseImageList = (post) => {
    const imageCollections = [post?.imageUrls, post?.images, post?.media, post?.galleryImages];
    for (const collection of imageCollections) {
        if (!Array.isArray(collection)) {
            continue;
        }
        return collection.map(resolveImageCandidate).filter(Boolean);
    }
    return [];
};

const normaliseSummary = (post, index = 0) => ({
    id: post.id ?? 1000 + index,
    participantId: post.participantId || "",
    title: post.title || "Untitled publication",
    coverImageUrl: post.coverImageUrl || "",
    publication: Boolean(
        post.publication ||
        post.publicationAuthors ||
        post.publicationYear ||
        post.venue ||
        post.abstractText ||
        post.keywords ||
        post.doi ||
        post.pdfUrl ||
        post.bibtex ||
        post.researchArea
    ),
    publicationAuthors: post.publicationAuthors || "",
    publicationYear: post.publicationYear || "",
    venue: post.venue || "",
    abstractText: post.abstractText || "",
    keywords: post.keywords || "",
    doi: post.doi || "",
    bibtex: post.bibtex || "",
    researchArea: post.researchArea || "",
    pdfUrl: post.pdfUrl || "",
    codeUrl: post.codeUrl || "",
    datasetUrl: post.datasetUrl || "",
    heritageName: post.heritageName || "",
    region: post.region || "",
    status: post.status || "PUBLISHED",
    authorName: post.authorName || post.authorNickname || "Anonymous Contributor",
    authorId: post.authorId == null ? "" : String(post.authorId),
    categoryId: post.categoryId == null ? "" : String(post.categoryId),
    categoryName: post.categoryName || "传统技艺",
    likeCount: Number(post.likeCount || 0),
    favoriteCount: Number(post.favoriteCount || 0),
    commentCount: Number(post.commentCount || 0),
    viewCount: Number(post.viewCount || 0),
    imageUrls: normaliseImageList(post),
    createdAt: post.createdAt || "2026-04-01T10:00:00"
});

const normaliseDetail = (detail, index = 0) => ({
    ...normaliseSummary(detail, index),
    content: detail.content || detail.abstractText || "",
    likedByCurrentUser: Boolean(detail.likedByCurrentUser),
    imageUrls: Array.isArray(detail.imageUrls) ? detail.imageUrls : [],
    comments: Array.isArray(detail.comments) ? detail.comments : []
});

const buildFallbackSummaries = () =>
    Object.values(fallbackPostDetails).map((detail, index) => normaliseSummary(detail, index));

const normaliseCategories = (items = []) => {
    const seen = new Set();
    const normalised = [];

    items.forEach((category, index) => {
        const name = category?.name;
        if (!name || seen.has(name)) {
            return;
        }

        seen.add(name);
        normalised.push({
            id: category.id ?? 500 + index,
            name,
            description: category.description || categoryDescriptionMap[name] || "",
            createdAt: category.createdAt || "2026-04-01T10:00:00"
        });
    });

    return normalised;
};

const buildFallbackCategories = () => normaliseCategories(fallbackCategories);

const mergeCategoriesWithFallback = (items = []) => {
    return normaliseCategories([...items, ...fallbackCategories]);
};

const mergePostsWithFallback = (items = []) => {
    const primary = items.map((post, index) => normaliseSummary(post, index));
    const merged = [...primary];
    const seenTitles = new Set(primary.map((post) => `${post.title}|${post.categoryName}`));

    for (const post of buildFallbackSummaries()) {
        const key = `${post.title}|${post.categoryName}`;
        if (seenTitles.has(key)) {
            continue;
        }
        merged.push(post);
        seenTitles.add(key);
        if (merged.length >= 8) {
            break;
        }
    }

    return merged;
};

createApp({
    data() {
        return {
            currentView: readStoredUser() ? "publish" : "auth",
            siteLanguage: readStoredSiteLanguage(),
            loading: false,
            errorMessage: "",
            successMessage: "",
            categories: [],
            posts: [],
            myPosts: [],
            adminPosts: [],
            pendingQueuePosts: [],
            selectedPostId: null,
            selectedPost: null,
            selectedCategoryFilter: "",
            selectedCategoryFocus: "",
            homeSearchQuery: "",
            appliedHomeSearchQuery: "",
            appliedCategoryFilter: "",
            homepageSearchLoading: false,
            defaultHomepagePostsCache: [],
            hasDefaultHomepagePostsCache: false,
            homepageRequestSequence: 0,
            latestHomepageRequestSequence: 0,
            currentPostPage: 1,
            postPageSize: 8,
            profileSection: "published",
            adminSection: "articles",
            currentUser: readStoredUser(),
            registerForm: createEmptyRegisterForm(),
            loginForm: {
                username: "",
                password: ""
            },
            passwordRecoveryLookupUsername: "",
            passwordRecoveryUsername: "",
            passwordRecoveryQuestions: [],
            passwordRecoveryAnswers: createEmptySecurityAnswers(),
            passwordRecoveryNewPassword: "",
            passwordRecoveryConfirmPassword: "",
            passwordChangeQuestions: [],
            passwordChangeAnswers: createEmptySecurityAnswers(),
            passwordChangeNewPassword: "",
            passwordChangeConfirmPassword: "",
            profileForm: {
                nickname: "",
                avatarUrl: "",
                bio: ""
            },
            profileEditingMode: "",
            profileEditTab: "profile",
            profileAvatarFileLabel: "No file selected",
            authMode: "login",
            authBanner: "",
            postForm: createEmptyPostForm(),
            editingPostId: null,
            editingPostStatus: "",
            editingRejectReason: "",
            coverFileLabel: "No file selected",
            galleryFileLabel: "No files selected",
            pdfFileLabel: "No PDF selected",
            pdfUploadLoading: false,
            pdfUploadProgress: 0,
            aiCoverGenerationLoading: false,
            aiCoverGenerationProgress: 0,
            aiCoverGenerationStage: "",
            aiCoverGenerationStartedAt: 0,
            aiCoverProgressTimer: null,
            aiCoverGenerationResult: null,
            aiCoverCandidates: [],
            aiCoverPreviewMode: "website",
            aiCoverCandidateFilter: "all",
            selectedSourceCandidateId: "",
            imageGenerationOptions: { audience: "public", communicationGoal: "show-application", imageStyle: "auto", sourcePolicy: "balanced", referenceMode: "auto", imageProviderMode: "dual", outputAspectRatio: "website-3:2" },
            imageStudioLanguage: "zh",
            aiCoverSelectionLoading: false,
            deepseekApiKeyInput: "",
            deepseekKeySaving: false,
            deepseekKeyStatus: {
                configured: false,
                source: "none",
                message: "Checking configuration..."
            },
            qwenApiKeyInput: "",
            qwenKeySaving: false,
            qwenKeyStatus: {
                configured: false,
                source: "none",
                message: "Checking configuration..."
            },
            providerSettingsSaving: false,
            providerSettings: {
                textProvider: "deepseek", textModel: "deepseek-v4-flash", textConfigured: false,
                imageProvider: "qwen", imageModel: "qwen-image-2.0-pro", imageConfigured: false,
                platformManaged: false
            },
            textProviderInput: "deepseek",
            textProviderModelInput: "deepseek-v4-flash",
            textProviderApiKeyInput: "",
            imageProviderInput: "qwen",
            imageProviderModelInput: "qwen-image-2.0-pro",
            imageProviderCustomModelInput: "",
            imageProviderApiKeyInput: "",
            imageVariantLoading: false,
            imageVariantDragging: false,
                        imageEditorInteraction: null,
            imageEditorResizeDirection: "",
            imageStretchBox: {
                x: 15,
                y: 15,
                width: 70,
                height: 70
            },
            imageNaturalWidth: 900,
            imageNaturalHeight: 600,
            imageVariantForm: {
                imageUrl: "",
                width: 900,
                height: 600,
                mode: "smart",
                focusX: 50,
                focusY: 50,
                                cropX: 0,
                cropY: 0,
                cropWidth: 100,
                cropHeight: 100,
                backgroundColor: "#F5F5F5"
            },
            xhsCoverEditorLoading: false,
            xhsCoverResultUrl: "",
            xhsCoverForm: {
                imageUrl: "",
                headline: "",
                textColor: "#202A31",
                backgroundColor: "#FFFFFF",
                showTitle: true,
                templatePreset: "classic"
            },
            aiContentGenerationLoading: false,
            aiContentGenerationStage: "",
            socialCopyLoading: false,
            socialCopyResult: null,
            socialCopyLanguage: "zh",
            socialCopyMode: "outreach",
            socialCopyTone: "engaging",
            socialCopyAudience: "public",
            socialCopyGoal: "science_education",
            socialCopyLength: "standard",
            socialCopyCallToAction: "",
            commentForm: {
                content: ""
            },
            currentCommentPage: 1,
            commentPageSize: 5,
            likeRequestPending: false,
            workspaceDrafts: [],
            workspacePending: [],
            contributorApplications: [],
            myContributorApplicationStatusFilter: "",
            adminUsers: [],
            adminContributorApplications: [],
            adminApprovals: [],
            adminFilterStatus: "",
            adminTitleQuery: "",
            adminSortOption: "UPDATED_DESC",
            pendingQueueQuery: "",
            pendingQueueReturnToApprovals: false,
            adminPostPage: 0,
            adminPostSize: 10,
            adminPostTotalElements: 0,
            adminPostTotalPages: 0,
            adminPostHasPrevious: false,
            adminPostHasNext: false,
            adminUserPage: 0,
            adminUserSize: 10,
            adminUserTotalElements: 0,
            adminUserTotalPages: 0,
            adminUserHasPrevious: false,
            adminUserHasNext: false,
            adminUserQuery: "",
            adminUserRoleFilter: "",
            adminUserActiveFilter: "",
            adminContributorApplicationQuery: "",
            adminContributorApplicationSortBy: "created_at_desc",
            contributorApplicationRejectReasons: {},
            selectedAdminPostId: null,
            selectedAdminPost: null,
            adminPreviewPostId: null,
            adminPreviewPost: null,
            imagePreviewOpen: false,
            imagePreviewImages: [],
            imagePreviewIndex: 0,
            imagePreviewZoom: 1,
            imagePreviewSource: "",
            adminReviewReason: "",
            draftCache: readStoredDraftCache(),
            likedPostMap: readStoredLikedPostMap(),
            likedPostIds: [],
            contributorApplicationForm: {
                applicationReason: "",
                attachment: null
            },
            attachmentFileLabel: "No file selected"
        };
    },
    computed: {
        textProviderLabel() {
            return { deepseek: "DeepSeek", openai: "OpenAI", doubao: "Doubao" }[this.textProviderInput] || "text provider";
        },
        imageProviderLabel() {
            return { qwen: "Qwen", openai: "OpenAI", doubao: "Doubao", comfyui: "ComfyUI" }[this.imageProviderInput] || "image provider";
        },
        textProviderModelOptions() {
            const models = {
                deepseek: ["deepseek-v4-flash", "deepseek-chat", "deepseek-reasoner"],
                openai: ["gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini"],
                doubao: ["doubao-seed-1-6-250615", "doubao-pro-32k-241215"]
            }[this.textProviderInput] || [];
            return this.withCurrentModel(models, this.textProviderModelInput);
        },
        imageProviderModelOptions() {
            const models = {
                qwen: ["qwen-image-2.0-pro", "qwen-image-2.0", "qwen-image-plus"],
                openai: ["gpt-image-1.5", "gpt-image-1"],
                doubao: ["doubao-seedream-4-5-251128", "doubao-seedream-4-0-250828", "custom-ark-endpoint"],
                comfyui: ["local-workflow"]
            }[this.imageProviderInput] || [];
            return this.withCurrentModel(models, this.imageProviderModelInput);
        },
        imageVariantStageAspect() {
            if (this.imageVariantForm.mode === "cover") {
                return `${Math.max(1, this.imageNaturalWidth)} / ${Math.max(1, this.imageNaturalHeight)}`;
            }
            if (this.imageVariantForm.mode === "stretch") {
                return "3 / 2";
            }
            return `${this.imageVariantForm.width} / ${this.imageVariantForm.height}`;
        },
        imageVariantPreviewStyle() {
            const mode = this.imageVariantForm.mode;
            const objectFit = mode === "stretch" || mode === "cover" ? "fill" : mode === "contain" ? "contain" : "cover";
            return {
                width: "100%",
                height: "100%",
                objectFit,
                objectPosition: `${this.imageVariantForm.focusX}% ${this.imageVariantForm.focusY}%`,
                display: "block",
                pointerEvents: "none"
            };
        },
        imageVariantStretchBoxStyle() {
            return {
                left: `${this.imageStretchBox.x}%`,
                top: `${this.imageStretchBox.y}%`,
                width: `${this.imageStretchBox.width}%`,
                height: `${this.imageStretchBox.height}%`
            };
        },
        imageVariantCropStyle() {
            return {
                left: `${this.imageVariantForm.cropX}%`,
                top: `${this.imageVariantForm.cropY}%`,
                width: `${this.imageVariantForm.cropWidth}%`,
                height: `${this.imageVariantForm.cropHeight}%`
            };
        },
        sourceAiCoverCandidates() {
            return this.aiCoverCandidates.filter((candidate) => candidate?.sourceType === "source-original");
        },
        generatedAiCoverCandidates() {
            return this.aiCoverCandidates.filter((candidate) => candidate?.sourceType !== "source-original");
        },
        selectedSourceAiCoverCandidate() {
            return this.sourceAiCoverCandidates.find((candidate) =>
                String(candidate?.candidateId || candidate?.imageUrl || "") === String(this.selectedSourceCandidateId || "")
            ) || this.sourceAiCoverCandidates[0] || null;
        },
        visibleAiCoverCandidates() {
            if (this.aiCoverCandidateFilter === "source") {
                return this.sourceAiCoverCandidates;
            }
            if (this.aiCoverCandidateFilter === "generated") {
                return this.generatedAiCoverCandidates;
            }
            return [this.selectedSourceAiCoverCandidate, ...this.generatedAiCoverCandidates].filter(Boolean);
        },
        isAdmin() {
            return this.currentUser?.role === "ADMIN";
        },
        isContributor() {
            return this.currentUser?.role === "CONTRIBUTOR" || this.currentUser?.role === "ADMIN";
        },
        canAccessContributorWorkspace() {
            return this.isContributor;
        },
        profileBioByteCount() {
            return this.countBytes(this.profileForm.bio);
        },
        postTitleByteLimit() {
            return postTitleByteLimit;
        },
        postContentByteLimit() {
            return postContentByteLimit;
        },
        postTitleByteCount() {
            return this.countBytes(this.postForm.title);
        },
        postContentByteCount() {
            return this.countBytes(this.postForm.content);
        },
        showPermissionRequestModule() {
            return this.currentUser?.role === "USER";
        },
        profileIdentityLabel() {
            if (this.isAdmin) {
                return "Administrator account";
            }
            if (this.isContributor) {
                return "Contributor account";
            }
            return "Member account";
        },
        profileIdentityHeading() {
            if (this.isAdmin) {
                return "Administrator Identity";
            }
            if (this.isContributor) {
                return "Contributor Identity";
            }
            return "User Identity";
        },
        currentViewLabel() {
            const labels = {
                home: "Homepage dashboard",
                detail: "Reading a publication",
                publish: this.editingPostId ? "Editing a publication draft" : "Uploading a publication",
                profile: "Personal workspace",
                admin: "Administration workspace",
                auth: "Managing account access"
            };
            return labels[this.currentView] || "Homepage dashboard";
        },
        totalStories() {
            return this.defaultHomepagePostsCache.length;
        },
        totalComments() {
            return this.defaultHomepagePostsCache.reduce((sum, post) => sum + Number(post.commentCount || 0), 0);
        },
        totalContributors() {
            return new Set(this.defaultHomepagePostsCache.map((post) => post.authorName).filter(Boolean)).size;
        },
        visibleCollectionCount() {
            return new Set(
                this.publishedHomepagePosts
                    .map((post) => String(post.researchArea || "").trim())
                    .filter(Boolean)
            ).size;
        },
        activeHomepageKeyword() {
            return this.appliedHomeSearchQuery.trim();
        },
        selectedCategoryFilterLabel() {
            if (!this.appliedCategoryFilter) {
                return "";
            }

            const categoryFilter = String(this.appliedCategoryFilter);
            const category = this.categories.find((item) => item.name === categoryFilter || String(item.id) === categoryFilter);
            if (category?.name) {
                return this.translateCategory(category.name);
            }

            const stat = this.categoryStats.find((item) => item.name === categoryFilter || String(item.id) === categoryFilter);
            if (stat?.name) {
                return this.translateCategory(stat.name);
            }

            return this.translateCategory(categoryFilter);
        },
        hasActiveHomepageFilters() {
            return Boolean(this.activeHomepageKeyword || this.appliedCategoryFilter);
        },
        homepageEmptyStateTitle() {
            return this.hasActiveHomepageFilters
                ? "No matching publications"
                : "No published publications are available yet";
        },
        homepageEmptyStateMessage() {
            const keyword = this.activeHomepageKeyword;
            const category = this.selectedCategoryFilterLabel || "the selected filter";

            if (keyword && this.appliedCategoryFilter) {
                return `No publications match "${keyword}" in ${category}. Try another keyword or clear one of the filters.`;
            }
            if (keyword) {
                return `No publications match "${keyword}". Try a broader keyword or clear the search.`;
            }
            if (this.appliedCategoryFilter) {
                return `No publications are available in ${category} right now. Try another filter or clear the filter.`;
            }
            return "The homepage feed is currently empty. Try refreshing again in a moment.";
        },
        hotRankingEmptyMessage() {
            return "No homepage ranking is available yet.";
        },
        filteredPosts() {
            return this.posts.filter((post) => post.publication);
        },
        articleResultsHeading() {
            return this.hasActiveHomepageFilters ? "Search Results" : "All Publications";
        },
        totalPostPages() {
            return Math.max(1, Math.ceil(this.filteredPosts.length / this.postPageSize));
        },
        hasPostPagination() {
            return this.filteredPosts.length > this.postPageSize;
        },
        paginatedPosts() {
            const page = Math.min(Math.max(this.currentPostPage, 1), this.totalPostPages);
            const start = (page - 1) * this.postPageSize;
            return this.filteredPosts.slice(start, start + this.postPageSize);
        },
        postResultCountLabel() {
            const total = this.filteredPosts.length;
            if (!total) {
                return "0 publications";
            }
            if (!this.hasPostPagination) {
                return `${total} ${total === 1 ? "publication" : "publications"}`;
            }
            const start = (Math.min(Math.max(this.currentPostPage, 1), this.totalPostPages) - 1) * this.postPageSize + 1;
            const end = Math.min(start + this.postPageSize - 1, total);
            return `${start}-${end} of ${total} publications`;
        },
        featuredPost() {
            return this.filteredPosts[0] || this.hotPosts[0] || this.posts[0] || null;
        },
        hotPosts() {
            return [...this.publishedHomepagePosts]
                .sort((left, right) => this.buildHeatScore(right) - this.buildHeatScore(left))
                .slice(0, 5);
        },
        categoryStats() {
            const publishedPosts = this.publishedHomepagePosts;
            const categories = this.categories.length
                ? this.categories
                : normaliseCategories(publishedPosts.map((post) => ({
                    id: post.categoryId || undefined,
                    name: post.categoryName,
                    description: categoryDescriptionMap[post.categoryName] || ""
                })));
            const counts = categories.map((category) => {
                const categoryId = String(category.id);
                const posts = publishedPosts.filter((post) => String(post.categoryId) === categoryId);
                return {
                    id: categoryId,
                    name: category.name,
                    description: category.description || categoryDescriptionMap[category.name] || "",
                    count: posts.length,
                    topPost: [...posts].sort((left, right) => this.buildHeatScore(right) - this.buildHeatScore(left))[0] || null
                };
            });

            const maxCount = counts.reduce((max, item) => Math.max(max, item.count), 0);

            return counts
                .map((item) => ({
                    ...item,
                    percentage: maxCount === 0 ? 0 : Math.max(14, Math.round((item.count / maxCount) * 100))
                }))
                .sort((left, right) => right.count - left.count);
        },
        categoryPieLegendItems() {
            const items = this.categoryStats.filter((stat) => stat.count > 0);
            const total = items.reduce((sum, stat) => sum + Number(stat.count || 0), 0);
            return items.map((stat, index) => {
                const count = Number(stat.count || 0);
                return {
                    id: stat.id,
                    name: this.translateCategory(stat.name),
                    count,
                    percent: total ? Math.round((count / total) * 100) : 0,
                    color: categoryPieColors[index % categoryPieColors.length]
                };
            });
        },
        focusedCategory() {
            const availableCategoryIds = this.categoryStats
                .filter((stat) => stat.count > 0)
                .map((stat) => String(stat.id));

            if (this.appliedCategoryFilter) {
                const appliedCategoryId = this.resolveCategoryId(this.appliedCategoryFilter) || String(this.appliedCategoryFilter);
                return availableCategoryIds.includes(appliedCategoryId)
                    ? appliedCategoryId
                    : "";
            }

            if (this.selectedCategoryFocus && availableCategoryIds.includes(this.selectedCategoryFocus)) {
                return this.selectedCategoryFocus;
            }

            return availableCategoryIds[0] || "";
        },
        focusedCategoryName() {
            if (!this.focusedCategory) {
                return "";
            }

            const stat = this.categoryStats.find((item) => String(item.id) === String(this.focusedCategory));
            if (stat) {
                return stat.name;
            }

            const category = this.categories.find((item) => String(item.id) === String(this.focusedCategory));
            return category?.name || "";
        },
        focusedCategoryPosts() {
            if (!this.focusedCategory) {
                return [];
            }
            return this.filteredPosts.filter((post) => String(post.categoryId) === String(this.focusedCategory));
        },
        profilePublishedPosts() {
            return this.myPosts.filter((post) => post.status === "PUBLISHED");
        },
        publishedHomepagePosts() {
            return this.defaultHomepagePostsCache.filter((post) => post.status === "PUBLISHED" && post.publication);
        },
        likedPosts() {
            return this.publishedHomepagePosts.filter((post) => this.likedPostIds.includes(Number(post.id)));
        },
        selectedPostLiked() {
            if (!this.selectedPost?.id) {
                return false;
            }
            return Boolean(this.selectedPost.likedByCurrentUser);
        },
        hasPasswordRecoveryQuestions() {
            return Array.isArray(this.passwordRecoveryQuestions)
                && this.passwordRecoveryQuestions.length === requiredSecurityQuestionCount;
        },
        hasPasswordChangeQuestions() {
            return Array.isArray(this.passwordChangeQuestions)
                && this.passwordChangeQuestions.length === requiredSecurityQuestionCount;
        },
        selectedPostComments() {
            return Array.isArray(this.selectedPost?.comments) ? this.selectedPost.comments : [];
        },
        totalCommentPages() {
            return Math.max(1, Math.ceil(this.selectedPostComments.length / this.commentPageSize));
        },
        hasCommentPagination() {
            return this.selectedPostComments.length > this.commentPageSize;
        },
        paginatedComments() {
            const page = Math.min(Math.max(this.currentCommentPage, 1), this.totalCommentPages);
            const start = (page - 1) * this.commentPageSize;
            return this.selectedPostComments.slice(start, start + this.commentPageSize);
        },
        commentPaginationLabel() {
            if (!this.selectedPostComments.length) {
                return "No comments";
            }
            return `Page ${Math.min(this.currentCommentPage, this.totalCommentPages)} of ${this.totalCommentPages}`;
        },
        profileSummary() {
            return {
                published: this.profilePublishedPosts.length,
                pending: this.workspacePending.length,
                drafts: this.workspaceDrafts.length,
                liked: this.likedPosts.length,
                requests: this.contributorApplications.length
            };
        },
        adminArticles() {
            return this.adminPosts;
        },
        aiCoverWarnings() {
            const warnings = [];
            const resultWarnings = this.aiCoverGenerationResult?.warnings;
            if (Array.isArray(resultWarnings)) {
                warnings.push(...resultWarnings);
            }
            const paperWarnings = this.aiCoverGenerationResult?.paperUnderstanding?.warnings;
            if (Array.isArray(paperWarnings)) {
                warnings.push(...paperWarnings);
            }
            this.aiCoverCandidates.forEach((candidate) => {
                if (Array.isArray(candidate?.warnings)) {
                    warnings.push(...candidate.warnings);
                }
            });
            return [...new Set(warnings.filter(Boolean))];
        },
        aiCoverUsesManualSelection() {
            return this.aiCoverCandidates.length > 0
                && !this.aiCoverCandidates.some((candidate) => this.isRankedAiCoverRecommendation(candidate));
        },
        aiCoverSceneDetails() {
            const scene = this.aiCoverGenerationResult?.applicationSceneBrief;
            if (!scene) {
                return [];
            }
            return [
                { label: "Scene type", value: scene.paperSceneType },
                { label: "Understanding source", value: scene.sourceProvider || this.aiCoverGenerationResult?.paperUnderstanding?.understandingSource },
                { label: "Target user", value: scene.targetUserOrActor || scene.targetUser },
                { label: "Proposed system", value: scene.proposedSystemOrMethod || scene.technologyOrProduct },
                { label: "Environment", value: scene.applicationEnvironment || scene.environment },
                { label: "Main task", value: scene.mainTaskOrWorkflow || scene.mainTask }
            ].filter((item) => item.value);
        },
        canGenerateSocialCopy() {
            if (!this.currentUser?.id || !this.selectedPost?.pdfUrl) {
                return false;
            }
            return this.isAdmin
                || String(this.selectedPost.authorId || "") === String(this.currentUser.id);
        },
        adminResultSummary() {
            const filters = [];
            if (this.adminFilterStatus) {
                filters.push(this.statusLabel(this.adminFilterStatus));
            }
            if (this.adminTitleQuery.trim()) {
                filters.push(`"${this.adminTitleQuery.trim()}"`);
            }
            return filters.length ? `Showing: ${filters.join(" · ")}` : "Showing: All publications";
        },
        adminResultCountLabel() {
            const currentCount = this.adminArticles.length;
            const totalCount = this.adminPostTotalElements || 0;
            if (!totalCount) {
                return `${currentCount} result${currentCount === 1 ? "" : "s"}`;
            }
            return `${currentCount} of ${totalCount} result${totalCount === 1 ? "" : "s"}`;
        },
        adminSortSummary() {
            return `Sorted by: ${this.adminSortLabel(this.adminSortOption)}`;
        },
        adminPaginationLabel() {
            if (!this.adminPostTotalPages) {
                return "Page 1 of 1";
            }
            return `Page ${this.adminPostPage + 1} of ${this.adminPostTotalPages}`;
        },
        adminUserPaginationLabel() {
            if (!this.adminUserTotalPages) {
                return "Page 1 of 1";
            }
            return `Page ${this.adminUserPage + 1} of ${this.adminUserTotalPages}`;
        },
        adminKpiCards() {
            const reviewQueueCount = this.adminApprovalsView.length;
            return [
                {
                    label: "Managed Publications",
                    value: this.adminArticles.length,
                    detail: "Draft, review, published, and archived publication states.",
                    microLabel: "Total",
                    badgeTone: "neutral",
                    targetSection: "articles",
                    isActive: this.adminSection === "articles",
                    isAlert: false
                },
                {
                    label: "Managed Users",
                    value: this.adminUserTotalElements || this.adminUsersView.length,
                    detail: "Accounts with role and activity controls.",
                    microLabel: "Total",
                    badgeTone: "neutral",
                    targetSection: "users",
                    isActive: this.adminSection === "users",
                    isAlert: false
                },
                {
                    label: "Contributor Applications",
                    value: this.adminContributorApplications.length,
                    detail: "Pending contributor access requests.",
                    microLabel: "Pending",
                    badgeTone: "pending",
                    targetSection: "contributor-review",
                    isActive: this.adminSection === "contributor-review",
                    isAlert: false
                },
                {
                    label: "Publication Review Queue",
                    value: reviewQueueCount,
                    detail: "Pending publication reviews waiting for action.",
                    microLabel: reviewQueueCount > 0 ? "Needs action" : "Clear",
                    badgeTone: reviewQueueCount > 0 ? "alert" : "clear",
                    targetSection: "approvals",
                    isActive: this.adminSection === "approvals",
                    isAlert: reviewQueueCount > 0
                }
            ];
        },
        adminSortColumnLabel() {
            if (this.adminSortOption === "SUBMITTED_DESC") {
                return "Submitted";
            }
            if (this.adminSortOption === "REVIEWED_DESC") {
                return "Reviewed";
            }
            return "Updated";
        },
        adminUsersView() {
            return this.adminUsers
                .map((user) => ({
                    ...user,
                    status: user.active ? "Active" : "Inactive",
                    contributions: user.contributionCount || 0
                }));
        },
        adminUserResultCountLabel() {
            const total = this.adminUserTotalElements;
            if (!total) {
                return "Showing 0 of 0 users";
            }

            const start = this.adminUserPage * this.adminUserSize + 1;
            const end = Math.min(start + this.adminUsersView.length - 1, total);
            return `Showing ${start}-${end} of ${total} users`;
        },
        adminUserFilterSummary() {
            const parts = [];

            if (this.adminUserRoleFilter) {
                parts.push(this.roleLabel(this.adminUserRoleFilter));
            }

            if (this.adminUserActiveFilter === "active") {
                parts.push("Active");
            } else if (this.adminUserActiveFilter === "inactive") {
                parts.push("Inactive");
            }

            if (this.adminUserQuery.trim()) {
                parts.push(`"${this.adminUserQuery.trim()}"`);
            }

            if (!parts.length) {
                return "Showing: All users";
            }

            return `Showing: ${parts.join(" · ")}`;
        },
        adminContributorApplicationsView() {
            const query = this.adminContributorApplicationQuery.trim().toLowerCase();
            return this.adminContributorApplications.filter((application) => {
                if (!query) {
                    return true;
                }
                return String(application.applicantUsername || "")
                    .toLowerCase()
                    .includes(query);
            });
        },
        adminContributorApplicationResultCountLabel() {
            const count = this.adminContributorApplicationsView.length;
            return `${count} result${count === 1 ? "" : "s"}`;
        },
        adminContributorApplicationFilterSummary() {
            const parts = [];

            if (this.adminContributorApplicationQuery.trim()) {
                parts.push(`"${this.adminContributorApplicationQuery.trim()}"`);
            }

            if (!parts.length) {
                return "Showing: Pending applications";
            }

            return `Showing: ${parts.join(" · ")}`;
        },
        adminContributorApplicationEmptyLabel() {
            if (!this.adminContributorApplications.length) {
                return "No pending contributor applications right now.";
            }
            return "No pending contributor applications match the current filter.";
        },
        adminApprovalsView() {
            const query = this.pendingQueueQuery.trim().toLowerCase();
            return this.pendingQueuePosts
                .filter((post) => !query || [post.title, this.translateText(post.title)]
                    .filter(Boolean)
                    .join(" ")
                    .toLowerCase()
                    .includes(query))
                .map((post) => ({
                    id: `approval-${post.id}`,
                    postId: post.id,
                    title: post.title,
                    authorName: post.authorName,
                    reviewerName: post.reviewerName || "",
                    status: post.status,
                    submittedAt: post.submittedAt || post.updatedAt
                }));
        },
        pendingQueueSummaryLabel() {
            const waitingCount = this.pendingQueuePosts.length;
            const filteredCount = this.adminApprovalsView.length;
            if (!waitingCount) {
                return "0 items waiting";
            }
            if (!this.pendingQueueQuery.trim()) {
                return `${waitingCount} item${waitingCount === 1 ? "" : "s"} waiting`;
            }
            return `${filteredCount} of ${waitingCount} item${waitingCount === 1 ? "" : "s"} waiting`;
        },
        pendingQueueSearchSummary() {
            if (!this.pendingQueueQuery.trim()) {
                return "Queue shows all pending items only.";
            }
            return `Searching queue titles for "${this.pendingQueueQuery.trim()}".`;
        },
        pendingQueueEmptyLabel() {
            if (!this.pendingQueuePosts.length) {
                return "No pending publication reviews right now.";
            }
            return "No pending publication reviews match the current queue search.";
        },
        myContributorApplicationsView() {
            return this.contributorApplications.filter((application) => {
                if (!this.myContributorApplicationStatusFilter) {
                    return true;
                }
                return application.status === this.myContributorApplicationStatusFilter;
            });
        },
        myContributorApplicationResultCountLabel() {
            const count = this.myContributorApplicationsView.length;
            return `${count} result${count === 1 ? "" : "s"}`;
        },
        myContributorApplicationFilterSummary() {
            if (!this.myContributorApplicationStatusFilter) {
                return "Showing: All requests";
            }
            return `Showing: ${this.statusLabel(this.myContributorApplicationStatusFilter)}`;
        },
        myContributorApplicationEmptyLabel() {
            if (!this.contributorApplications.length) {
                return "No contributor applications submitted yet.";
            }
            return "No contributor applications match the current filter.";
        },
        canApplyForContributor() {
            return this.currentUser?.role === "USER"
                && !this.contributorApplications.some((application) => application.status === "PENDING");
        },
        latestContributorApplication() {
            return this.contributorApplications[0] || null;
        },
        publicationWorkflowHint() {
            return this.siteLanguage === "zh"
                ? "填写 Participant ID 和论文标题，上传 PDF 后即可生成文案与图片。"
                : "Enter a Participant ID and paper title, then upload the PDF to generate copy and images.";
        },
        canSubmitForReview() {
            return !this.isAdmin && Boolean(this.editingPostId) && ["DRAFT", "REJECTED"].includes(this.editingPostStatus);
        },
        publishPrimaryLabel() {
            if (this.isAdmin) {
                return this.editingPostId ? "Update publication" : "Publish publication";
            }
            return this.editingPostId ? "Update draft" : "Save draft";
        },
        currentPreviewImage() {
            return this.imagePreviewImages[this.imagePreviewIndex] || "";
        },
        hasPreviewNavigation() {
            return this.imagePreviewImages.length > 1;
        },
        imagePreviewScaleLabel() {
            return `${Math.round(this.imagePreviewZoom * 100)}%`;
        }
    },
    watch: {
        defaultHomepagePostsCache: {
            handler() {
                this.$nextTick(() => {
                    if (["home", "admin"].includes(this.currentView)) {
                        this.renderCharts();
                    }
                });
            },
            deep: true
        }
    },
    async mounted() {
        this.applySiteLanguage(this.siteLanguage, false);
        await this.bootstrapTestSession();
        if (this.currentUser) {
            this.syncProfileFormFromCurrentUser();
            this.syncLikedPostsFromStorage();
            await this.refreshWorkspaceData();
        }
        this.handleHash();
        window.addEventListener("hashchange", this.handleHash);
        window.addEventListener("keydown", this.handleGlobalKeydown);
        this.syncBodyScrollLock();
    },
    beforeUnmount() {
        window.removeEventListener("hashchange", this.handleHash);
        window.removeEventListener("keydown", this.handleGlobalKeydown);
        window.removeEventListener("resize", this.handleChartResize);
        window.removeEventListener("pointermove", this.updateImageEditorInteraction);
        window.removeEventListener("pointerup", this.endImageEditorInteraction);
        window.removeEventListener("pointercancel", this.endImageEditorInteraction);
        window.removeEventListener("pointermove", this.updateImageVariantFocus);
        window.removeEventListener("pointerup", this.endImageVariantFocus);
        document.body.classList.remove("body--modal-open");
        document.body.classList.remove("body--image-resizing");
        delete document.body.dataset.imageResizeDirection;
    },
    methods: {
        async bootstrapTestSession() {
            try {
                const response = await fetch("/api/auth/test-session", { method: "POST" });
                if (!response.ok) {
                    return false;
                }
                const payload = await response.json();
                if (!payload.success || !payload.data?.token) {
                    return false;
                }
                this.currentUser = payload.data;
                this.storeUser(payload.data);
                return true;
            } catch {
                return false;
            }
        },
        redirectGuestToRegister(message = "Sign in before viewing your profile.") {
            this.currentView = "auth";
            this.authMode = "login";
            this.authBanner = message;
            this.errorMessage = "";
            window.location.hash = "#auth";
            this.focusAuthInput("loginUsernameInput");
        },
        currentLikedPostsKey() {
            return this.currentUser?.id ? String(this.currentUser.id) : "";
        },
        syncLikedPostsFromStorage() {
            const key = this.currentLikedPostsKey();
            if (!key) {
                this.likedPostIds = [];
                return;
            }
            const stored = this.likedPostMap[key];
            this.likedPostIds = Array.isArray(stored)
                ? [...new Set(stored.map((id) => Number(id)).filter((id) => Number.isFinite(id)))]
                : [];
        },
        persistLikedPosts() {
            localStorage.setItem(likedPostsStorageKey, JSON.stringify(this.likedPostMap));
        },
        isPostLiked(postId) {
            return this.likedPostIds.includes(Number(postId));
        },
        displayLikeCount(post) {
            if (!post) {
                return 0;
            }
            return Number(post.likeCount || 0);
        },
        applyPostLikeState(postId, likeCount, likedByCurrentUser) {
            const matchesPost = (post) => String(post?.id) === String(postId);
            const applyState = (post) => {
                if (!post) {
                    return;
                }
                post.likeCount = Number(likeCount || 0);
                post.likedByCurrentUser = Boolean(likedByCurrentUser);
            };

            if (matchesPost(this.selectedPost)) {
                applyState(this.selectedPost);
            }
            applyState(this.defaultHomepagePostsCache.find(matchesPost));
            applyState(this.posts.find(matchesPost));
            if (fallbackPostDetails[postId]) {
                applyState(fallbackPostDetails[postId]);
            }
        },
        applyPostCommentCount(postId, commentCount) {
            const matchesPost = (post) => String(post?.id) === String(postId);
            const nextCount = Math.max(0, Number(commentCount || 0));
            const applyState = (post) => {
                if (post) {
                    post.commentCount = nextCount;
                }
            };

            if (matchesPost(this.selectedPost)) {
                applyState(this.selectedPost);
            }
            applyState(this.defaultHomepagePostsCache.find(matchesPost));
            applyState(this.posts.find(matchesPost));
            if (fallbackPostDetails[postId]) {
                applyState(fallbackPostDetails[postId]);
            }
        },
        canDeleteComment(comment) {
            if (this.isAdmin) {
                return Boolean(this.currentUser?.id && comment?.id);
            }
            return Boolean(this.currentUser?.id && comment?.authorId && String(comment.authorId) === String(this.currentUser.id));
        },
        setStoredLike(postId, liked) {
            const key = this.currentLikedPostsKey();
            if (!key) {
                return;
            }
            const numericPostId = Number(postId);
            const current = new Set(this.likedPostIds.map((id) => Number(id)));
            if (liked) {
                current.add(numericPostId);
            } else {
                current.delete(numericPostId);
            }
            this.likedPostIds = [...current];
            this.likedPostMap[key] = [...this.likedPostIds];
            this.persistLikedPosts();
        },
        async toggleLike(post, syncOnly = false) {
            if (!post?.id) {
                return;
            }
            if (!this.currentUser) {
                this.redirectGuestToRegister("Sign in before opening your profile or liking publications.");
                return;
            }

            const postId = Number(post.id);
            if (syncOnly) {
                this.setStoredLike(postId, true);
                return;
            }

            if (fallbackPostDetails[postId]) {
                const liked = !this.isPostLiked(postId);
                const likeCount = Math.max(0, Number(post.likeCount || 0) + (liked ? 1 : -1));
                this.applyPostLikeState(postId, likeCount, liked);
                this.setStoredLike(postId, liked);
                this.showSuccess(liked ? "Publication liked successfully." : "Publication like removed.");
                return;
            }

            if (this.likeRequestPending) {
                return;
            }
            this.likeRequestPending = true;
            try {
                const data = await this.request(`/api/posts/${postId}/like`, {
                    method: "POST"
                }, "", null, true);
                if (data) {
                    const liked = Boolean(data.likedByCurrentUser);
                    if (String(this.selectedPost?.id) === String(postId)) {
                        this.selectedPost = normaliseDetail(data);
                    }
                    this.applyPostLikeState(postId, data.likeCount, liked);
                    this.setStoredLike(postId, liked);
                    this.showSuccess(liked ? "Publication liked successfully." : "Publication like removed.");
                }
            } finally {
                this.likeRequestPending = false;
            }
        },
        syncProfileFormFromCurrentUser() {
            this.profileForm.nickname = this.currentUser?.nickname || "";
            this.profileForm.avatarUrl = this.currentUser?.avatarUrl || "";
            this.profileForm.bio = this.currentUser?.bio || "";
            this.profileAvatarFileLabel = "No file selected";
        },
        mergeCurrentUserProfile(profile) {
            if (!this.currentUser || !profile) {
                return;
            }
            this.currentUser = {
                ...this.currentUser,
                ...profile
            };
            this.storeUser(this.currentUser);
            this.syncProfileFormFromCurrentUser();
        },
        ensureAccessibleProfileSection() {
            const allowedSections = this.canAccessContributorWorkspace
                ? ["published", "pending", "drafts", "liked"]
                : (this.showPermissionRequestModule ? ["liked", "permissions"] : ["liked"]);
            if (!allowedSections.includes(this.profileSection)) {
                this.profileSection = allowedSections[0];
            }
        },
        openProfileEditor(mode) {
            const nextTab = mode === "password" ? "password" : "profile";
            this.profileEditingMode = "profile";
            this.profileEditTab = nextTab;
            this.syncProfileFormFromCurrentUser();
        },
        closeProfileEditor() {
            this.profileEditingMode = "";
            this.profileEditTab = "profile";
            this.resetPasswordChangeForm();
            this.syncProfileFormFromCurrentUser();
        },
        selectProfileEditTab(tab) {
            if (!["profile", "password"].includes(tab)) {
                return;
            }
            this.profileEditTab = tab;
        },
        navigate(view, updateHash = true) {
            let nextView = view;

            if (this.imagePreviewOpen) {
                this.closeImagePreview();
            }

            if (["publish", "settings"].includes(view) && !this.currentUser) {
                this.redirectGuestToRegister(this.siteLanguage === "zh" ? "请先登录后再使用生成工作台。" : "Sign in before using the generation studio.");
                return;
            }

            if (["home", "profile", "detail", "admin", "categories"].includes(view)) {
                nextView = this.currentUser ? "publish" : "auth";
            }

            if (nextView !== "admin") {
                this.closeAdminPostPreview();
            }

            this.currentView = nextView;
            if (nextView === "auth") {
                this.authMode = "login";
                this.resetPasswordRecoveryForm();
            }
            if (nextView === "profile" && this.currentUser) {
                this.ensureAccessibleProfileSection();
                this.refreshWorkspaceData();
            }
            if (nextView === "admin" && this.isAdmin) {
                this.warmAdminDashboardData();
            }
            if (["detail", "publish", "admin", "settings"].includes(nextView) && this.currentUser) {
                this.loadDeepSeekKeyStatus();
            }
            if (nextView === "settings" && this.currentUser) {
                this.loadQwenKeyStatus();
                this.loadProviderSettings();
            }
            if (["home", "admin"].includes(nextView)) {
                this.$nextTick(() => {
                    this.renderCharts();
                });
            }
            if (updateHash) {
                window.location.hash = `#${nextView}`;
            }
        },
        handleHash() {
            const hash = window.location.hash.replace("#", "");
            if (this.imagePreviewOpen) {
                this.closeImagePreview();
            }
            if (!hash || ["home", "profile", "detail", "admin", "categories"].includes(hash) || hash.startsWith("detail-")) {
                this.navigate(this.currentUser ? "publish" : "auth", false);
                return;
            }

            const supported = ["publish", "settings", "auth"];
            if (supported.includes(hash)) {
                this.navigate(hash, false);
            } else {
                this.navigate(this.currentUser ? "publish" : "auth", false);
            }
        },
        handleGlobalKeydown(event) {
            if (this.imagePreviewOpen) {
                if (event.key === "Escape") {
                    this.closeImagePreview();
                    return;
                }
                if (event.key === "ArrowLeft" && this.hasPreviewNavigation) {
                    event.preventDefault();
                    this.showPreviousPreviewImage();
                    return;
                }
                if (event.key === "ArrowRight" && this.hasPreviewNavigation) {
                    event.preventDefault();
                    this.showNextPreviewImage();
                    return;
                }
            }

            if (event.key === "Escape" && this.adminPreviewPost) {
                this.closeAdminPostPreview();
            }
        },
        syncBodyScrollLock() {
            document.body.classList.toggle("body--modal-open", this.imagePreviewOpen || Boolean(this.adminPreviewPost));
        },
        buildPreviewImageList(images = []) {
            return [...new Set((Array.isArray(images) ? images : []).filter(Boolean))];
        },
        openImagePreview(images = [], startIndex = 0, source = "detail") {
            const previewImages = this.buildPreviewImageList(images);
            if (!previewImages.length) {
                return;
            }

            const safeIndex = Math.min(Math.max(Number(startIndex) || 0, 0), previewImages.length - 1);
            this.imagePreviewImages = previewImages;
            this.imagePreviewIndex = safeIndex;
            this.imagePreviewZoom = 1;
            this.imagePreviewSource = source;
            this.imagePreviewOpen = true;
            this.syncBodyScrollLock();
        },
        closeImagePreview() {
            this.imagePreviewOpen = false;
            this.imagePreviewImages = [];
            this.imagePreviewIndex = 0;
            this.imagePreviewZoom = 1;
            this.imagePreviewSource = "";
            this.syncBodyScrollLock();
        },
        resetImagePreviewZoom() {
            this.imagePreviewZoom = 1;
        },
        updateImagePreviewZoom(nextZoom) {
            const boundedZoom = Math.min(Math.max(nextZoom, 0.6), 3);
            this.imagePreviewZoom = Math.round(boundedZoom * 100) / 100;
        },
        zoomImagePreviewIn() {
            this.updateImagePreviewZoom(this.imagePreviewZoom + 0.2);
        },
        zoomImagePreviewOut() {
            this.updateImagePreviewZoom(this.imagePreviewZoom - 0.2);
        },
        handleImagePreviewWheel(event) {
            if (!this.imagePreviewOpen) {
                return;
            }
            event.preventDefault();
            if (event.deltaY < 0) {
                this.zoomImagePreviewIn();
                return;
            }
            this.zoomImagePreviewOut();
        },
        showPreviewImage(index) {
            if (!this.imagePreviewImages.length) {
                return;
            }
            const safeIndex = Math.min(Math.max(Number(index) || 0, 0), this.imagePreviewImages.length - 1);
            this.imagePreviewIndex = safeIndex;
            this.resetImagePreviewZoom();
        },
        showPreviousPreviewImage() {
            if (!this.hasPreviewNavigation) {
                return;
            }
            const nextIndex = (this.imagePreviewIndex - 1 + this.imagePreviewImages.length) % this.imagePreviewImages.length;
            this.showPreviewImage(nextIndex);
        },
        showNextPreviewImage() {
            if (!this.hasPreviewNavigation) {
                return;
            }
            const nextIndex = (this.imagePreviewIndex + 1) % this.imagePreviewImages.length;
            this.showPreviewImage(nextIndex);
        },
        openDetailGalleryPreview(startIndex = 0) {
            this.openImagePreview(this.selectedPost?.imageUrls || [], startIndex, "public-detail");
        },
        buildAdminPreviewImages() {
            if (!this.adminPreviewPost) {
                return [];
            }
            return this.buildPreviewImageList([
                this.getPostOwnCoverImage(this.adminPreviewPost),
                ...(Array.isArray(this.adminPreviewPost.imageUrls) ? this.adminPreviewPost.imageUrls : [])
            ]);
        },
        openAdminCoverPreview() {
            this.openImagePreview(this.buildAdminPreviewImages(), 0, "admin-preview");
        },
        openAdminGalleryPreview(imageUrl, galleryIndex = 0) {
            const previewImages = this.buildAdminPreviewImages();
            const previewIndex = previewImages.indexOf(imageUrl);
            this.openImagePreview(previewImages, previewIndex >= 0 ? previewIndex : galleryIndex + 1, "admin-preview");
        },
        authHeaders(baseHeaders = {}) {
            const headers = { ...baseHeaders };
            if (this.currentUser?.token) {
                headers["Authorization"] = `Bearer ${this.currentUser.token}`;
            }
            return headers;
        },
        async warmAdminDashboardData() {
            if (!this.currentUser) {
                return;
            }
            await Promise.all([
                this.fetchAdminPosts(),
                this.fetchAdminUsers(),
                this.fetchAdminContributorApplications(),
                this.fetchPendingQueue()
            ]);
        },
        handleChartResize() {
            ["chart-posts", "chart-comments", "chart-categories"].forEach((id) => {
                const dom = document.getElementById(id);
                if (dom && typeof echarts !== "undefined") {
                    echarts.getInstanceByDom(dom)?.resize();
                }
            });
        },
        readHomepageSearchStateFromUrl() {
            const params = new URLSearchParams(window.location.search);
            const categoryName = (params.get("category") || "").trim();
            const legacyCategoryId = (params.get("categoryId") || "").trim();
            return {
                keyword: (params.get("keyword") || "").trim(),
                categoryName: categoryName || this.resolveCategoryName(legacyCategoryId)
            };
        },
        syncHomepageSearchStateToUrl() {
            const url = new URL(window.location.href);
            const keyword = this.appliedHomeSearchQuery.trim();
            const categoryName = this.appliedCategoryFilter ? String(this.appliedCategoryFilter) : "";

            if (keyword) {
                url.searchParams.set("keyword", keyword);
            } else {
                url.searchParams.delete("keyword");
            }

            url.searchParams.delete("categoryId");
            if (categoryName) {
                url.searchParams.set("category", categoryName);
            } else {
                url.searchParams.delete("category");
            }

            const nextSearch = url.searchParams.toString();
            const nextUrl = `${url.pathname}${nextSearch ? `?${nextSearch}` : ""}${url.hash}`;
            window.history.replaceState({}, "", nextUrl);
        },
        cacheDefaultHomepagePosts(posts) {
            this.defaultHomepagePostsCache = clone(posts);
            this.hasDefaultHomepagePostsCache = true;
        },
        applyHomepageSearchState(keyword, categoryName) {
            this.appliedHomeSearchQuery = (keyword || "").trim();
            this.appliedCategoryFilter = categoryName ? String(categoryName).trim() : "";
        },
        normaliseHomepagePosts(items = []) {
            return items.map((post, index) => {
                const summary = normaliseSummary(post, index);
                return {
                    ...summary,
                    categoryId: summary.categoryId || this.resolveCategoryId(summary.categoryName)
                };
            });
        },
        resetPostPagination() {
            this.currentPostPage = 1;
        },
        ensurePostPageInRange() {
            this.currentPostPage = Math.min(Math.max(Number(this.currentPostPage) || 1, 1), this.totalPostPages);
        },
        previousPostPage() {
            this.goToPostPage(this.currentPostPage - 1);
        },
        nextPostPage() {
            this.goToPostPage(this.currentPostPage + 1);
        },
        goToPostPage(page) {
            const nextPage = Math.min(Math.max(Number(page) || 1, 1), this.totalPostPages);
            this.currentPostPage = nextPage;
        },
        async fetchHomepageData() {
            this.errorMessage = "";
            await this.fetchCategories();
            const initialState = this.readHomepageSearchStateFromUrl();
            this.homeSearchQuery = initialState.keyword;
            this.selectedCategoryFilter = initialState.categoryName;
            if (initialState.keyword || initialState.categoryName) {
                await this.ensureDefaultHomepagePostsCache();
            }
            this.applyHomepageSearchState(initialState.keyword, initialState.categoryName);
            await this.fetchPosts({ allowFallback: true });
            window.addEventListener("resize", this.handleChartResize);
        },
        async fetchCategories() {
            try {
                const response = await fetch("/api/categories");
                const payload = await response.json();
                if (!response.ok || !payload?.success || !Array.isArray(payload.data)) {
                    throw new Error(this.translateBackendMessage(payload?.message) || "Unable to load homepage categories.");
                }
                this.categories = normaliseCategories(payload.data);
            } catch (error) {
                this.categories = buildFallbackCategories();
            } finally {
                if (!this.selectedCategoryFocus && this.categories.length) {
                    this.selectedCategoryFocus = String(this.categories[0].id);
                }
            }
        },
        async fetchPosts(options = {}) {
            const {
                allowFallback = false,
                showGlobalLoading = true,
                useHomepageSearchLoading = false
            } = options;
            const params = new URLSearchParams();
            const keyword = this.homeSearchQuery.trim();
            const categoryName = this.selectedCategoryFilter ? String(this.selectedCategoryFilter).trim() : "";
            if (keyword) {
                params.set("keyword", keyword);
            }
            if (categoryName) {
                params.set("category", categoryName);
            }
            const query = params.toString();
            const requestSequence = ++this.homepageRequestSequence;
            this.latestHomepageRequestSequence = requestSequence;
            const isDefaultRequest = !keyword && !categoryName;

            this.applyHomepageSearchState(keyword, categoryName);
            this.syncHomepageSearchStateToUrl();

            if (showGlobalLoading) {
                this.loading = true;
            }
            if (useHomepageSearchLoading) {
                this.homepageSearchLoading = true;
            }
            this.errorMessage = "";
            try {
                const response = await fetch(`/api/posts${query ? `?${query}` : ""}`);
                const payload = await response.json();
                if (!response.ok || !payload?.success || !Array.isArray(payload.data)) {
                    throw new Error(this.translateBackendMessage(payload?.message) || "Unable to load the homepage publication feed.");
                }
                if (requestSequence !== this.latestHomepageRequestSequence) {
                    return;
                }
                const normalisedPosts = this.normaliseHomepagePosts(payload.data);
                this.posts = normalisedPosts;
                this.ensurePostPageInRange();
                if (isDefaultRequest) {
                    this.cacheDefaultHomepagePosts(normalisedPosts);
                }
            } catch (error) {
                if (requestSequence !== this.latestHomepageRequestSequence) {
                    return;
                }
                if (allowFallback) {
                    const fallbackPosts = this.normaliseHomepagePosts(buildFallbackSummaries());
                    this.posts = fallbackPosts;
                    this.ensurePostPageInRange();
                    if (isDefaultRequest) {
                        this.cacheDefaultHomepagePosts(fallbackPosts);
                    }
                    this.showError("The live homepage feed is unavailable.");
                } else {
                    this.posts = [];
                    this.ensurePostPageInRange();
                    this.showError(error.message || "Unable to load the homepage publication feed.");
                }
            } finally {
                if (requestSequence === this.latestHomepageRequestSequence) {
                    if (showGlobalLoading) {
                        this.loading = false;
                    }
                    if (useHomepageSearchLoading) {
                        this.homepageSearchLoading = false;
                    }
                }
            }
        },
        async ensureDefaultHomepagePostsCache() {
            if (this.hasDefaultHomepagePostsCache) {
                return;
            }

            try {
                const response = await fetch("/api/posts");
                const payload = await response.json();
                if (!response.ok || !payload?.success || !Array.isArray(payload.data)) {
                    throw new Error(this.translateBackendMessage(payload?.message) || "Unable to load the default homepage publication feed.");
                }
                this.cacheDefaultHomepagePosts(this.normaliseHomepagePosts(payload.data));
            } catch (error) {
                this.cacheDefaultHomepagePosts(this.normaliseHomepagePosts(buildFallbackSummaries()));
            }
        },
        async triggerHomepageSearch() {
            const nextKeyword = this.homeSearchQuery.trim();
            const nextCategoryName = this.selectedCategoryFilter ? String(this.selectedCategoryFilter).trim() : "";

            this.resetPostPagination();
            this.applyHomepageSearchState(nextKeyword, nextCategoryName);
            this.syncHomepageSearchStateToUrl();

            await this.fetchPosts({
                allowFallback: !nextKeyword && !nextCategoryName,
                showGlobalLoading: false,
                useHomepageSearchLoading: true
            });
            this.scrollToArticleResults();
        },
        async handleHomepageSearchEnter() {
            await this.triggerHomepageSearch();
        },
        async fetchMyProfile() {
            if (!this.currentUser) {
                return;
            }
            const data = await this.request("/api/my/profile", {
                method: "GET"
            }, "", null, true);
            if (data) {
                this.mergeCurrentUserProfile(data);
            }
        },
        async fetchMyPosts() {
            if (!this.currentUser) {
                this.myPosts = [];
                this.workspaceDrafts = [];
                this.workspacePending = [];
                return;
            }
            const data = await this.request("/api/my/posts", {
                method: "GET"
            }, "", null, true);
            if (!Array.isArray(data)) {
                return;
            }
            const visiblePosts = data.filter((post) => post.status !== "ARCHIVED");
            this.myPosts = visiblePosts;
            this.workspaceDrafts = visiblePosts.filter((post) => post.status === "DRAFT" || post.status === "REJECTED");
            this.workspacePending = visiblePosts.filter((post) => post.status === "PENDING_REVIEW");
            if (this.editingPostId) {
                const currentEditingPost = visiblePosts.find((post) => post.id === this.editingPostId);
                if (currentEditingPost) {
                    this.editingPostStatus = currentEditingPost.status;
                    this.editingRejectReason = currentEditingPost.rejectReason || "";
                }
            }
        },
        async fetchMyContributorApplications() {
            if (!this.currentUser) {
                this.contributorApplications = [];
                return;
            }
            const data = await this.request("/api/my/contributor-applications", {
                method: "GET"
            }, "", null, true);
            if (Array.isArray(data)) {
                this.contributorApplications = data;
            }
        },
        resetPasswordChangeForm() {
            this.passwordChangeQuestions = [];
            this.passwordChangeAnswers = createEmptySecurityAnswers();
            this.passwordChangeNewPassword = "";
            this.passwordChangeConfirmPassword = "";
        },
        async loadMyPasswordSecurityQuestions() {
            if (!this.currentUser) {
                this.resetPasswordChangeForm();
                return;
            }
            const data = await this.request("/api/my/profile/password-security-questions", {
                method: "GET"
            }, "", null, true);
            if (Array.isArray(data) && data.length === requiredSecurityQuestionCount) {
                this.passwordChangeQuestions = data;
                this.passwordChangeAnswers = createEmptySecurityAnswers();
            }
        },
        async fetchAdminPosts() {
            if (!this.isAdmin) {
                this.adminPosts = [];
                this.adminPostTotalElements = 0;
                this.adminPostTotalPages = 0;
                this.adminPostHasPrevious = false;
                this.adminPostHasNext = false;
                return;
            }
            const params = new URLSearchParams();
            if (this.adminFilterStatus) {
                params.set("status", this.adminFilterStatus);
            }
            if (this.adminTitleQuery.trim()) {
                params.set("title", this.adminTitleQuery.trim());
            }
            if (this.adminSortOption) {
                params.set("sort", this.adminSortOption);
            }
            params.set("page", String(this.adminPostPage));
            params.set("size", String(this.adminPostSize));
            const query = params.toString();
            this.errorMessage = "";
            try {
                const response = await fetch(`/api/admin/posts${query ? `?${query}` : ""}`, {
                    method: "GET",
                    headers: this.authHeaders()
                });
                const payload = await response.json();
                if (!payload.success) {
                    throw new Error(this.translateBackendMessage(payload.message) || "The request could not be completed.");
                }
                const data = Array.isArray(payload.data) ? payload.data : [];
                const totalElements = Number(response.headers.get("X-Total-Elements") || data.length || 0);
                const totalPages = Number(response.headers.get("X-Total-Pages") || (data.length ? 1 : 0));
                const hasPrevious = (response.headers.get("X-Has-Previous") || "false") === "true";
                const hasNext = (response.headers.get("X-Has-Next") || "false") === "true";

                if (this.adminPostPage > 0 && !data.length && totalElements > 0) {
                    this.adminPostPage -= 1;
                    await this.fetchAdminPosts();
                    return;
                }

                this.adminPosts = data;
                this.adminPostTotalElements = totalElements;
                this.adminPostTotalPages = totalPages;
                this.adminPostHasPrevious = hasPrevious;
                this.adminPostHasNext = hasNext;
            } catch (error) {
                this.showError(error.message || "The request could not be completed.");
            }
        },
        async fetchPendingQueue() {
            if (!this.isAdmin) {
                this.pendingQueuePosts = [];
                return;
            }
            const data = await this.request("/api/admin/posts?status=PENDING_REVIEW", {
                method: "GET"
            }, "", null, true);
            if (Array.isArray(data)) {
                this.pendingQueuePosts = data;
            }
        },
        async fetchAdminContributorApplications() {
            if (!this.isAdmin) {
                this.adminContributorApplications = [];
                return;
            }
            const params = new URLSearchParams();
            params.set("status", "PENDING");
            if (this.adminContributorApplicationSortBy) {
                params.set("sortBy", this.adminContributorApplicationSortBy);
            }
            const query = params.toString();
            const data = await this.request(`/api/admin/contributor-applications${query ? `?${query}` : ""}`, {
                method: "GET"
            }, "", null, true);
            if (Array.isArray(data)) {
                this.adminContributorApplications = data;
                const nextRejectReasons = {};
                for (const application of data) {
                    nextRejectReasons[application.id] = this.contributorApplicationRejectReasons[application.id] || "";
                }
                this.contributorApplicationRejectReasons = nextRejectReasons;
            }
        },
        async fetchAdminUsers() {
            if (!this.isAdmin) {
                this.adminUsers = [];
                this.adminUserTotalElements = 0;
                this.adminUserTotalPages = 0;
                this.adminUserHasPrevious = false;
                this.adminUserHasNext = false;
                return;
            }
            const params = new URLSearchParams();
            if (this.adminUserQuery.trim()) {
                params.set("username", this.adminUserQuery.trim());
            }
            if (this.adminUserRoleFilter) {
                params.set("role", this.adminUserRoleFilter);
            }
            if (this.adminUserActiveFilter === "active") {
                params.set("active", "true");
            } else if (this.adminUserActiveFilter === "inactive") {
                params.set("active", "false");
            }
            params.set("page", String(this.adminUserPage));
            params.set("size", String(this.adminUserSize));
            const query = params.toString();
            this.errorMessage = "";
            try {
                const response = await fetch(`/api/admin/users${query ? `?${query}` : ""}`, {
                    method: "GET",
                    headers: this.authHeaders()
                });
                const payload = await response.json();
                if (!payload.success) {
                    throw new Error(this.translateBackendMessage(payload.message) || "The request could not be completed.");
                }
                const data = Array.isArray(payload.data) ? payload.data : [];
                const totalElements = Number(response.headers.get("X-Total-Elements") || data.length || 0);
                const totalPages = Number(response.headers.get("X-Total-Pages") || (data.length ? 1 : 0));
                const hasPrevious = (response.headers.get("X-Has-Previous") || "false") === "true";
                const hasNext = (response.headers.get("X-Has-Next") || "false") === "true";

                if (this.adminUserPage > 0 && !data.length && totalElements > 0) {
                    this.adminUserPage -= 1;
                    await this.fetchAdminUsers();
                    return;
                }

                this.adminUsers = data;
                this.adminUserTotalElements = totalElements;
                this.adminUserTotalPages = totalPages;
                this.adminUserHasPrevious = hasPrevious;
                this.adminUserHasNext = hasNext;
            } catch (error) {
                this.showError(error.message || "The request could not be completed.");
            }
        },
        async searchAdminPosts() {
            this.adminPostPage = 0;
            await this.fetchAdminPosts();
        },
        async clearAdminSearch() {
            this.adminTitleQuery = "";
            this.adminFilterStatus = "";
            this.adminSortOption = "UPDATED_DESC";
            this.adminPostPage = 0;
            this.adminPostSize = 10;
            await this.fetchAdminPosts();
        },
        async goToPreviousAdminPostPage() {
            if (!this.adminPostHasPrevious || this.adminPostPage <= 0) {
                return;
            }
            this.adminPostPage -= 1;
            await this.fetchAdminPosts();
        },
        async goToNextAdminPostPage() {
            if (!this.adminPostHasNext) {
                return;
            }
            this.adminPostPage += 1;
            await this.fetchAdminPosts();
        },
        async changeAdminPostSize() {
            this.adminPostPage = 0;
            await this.fetchAdminPosts();
        },
        async searchAdminUsers() {
            this.adminUserPage = 0;
            await this.fetchAdminUsers();
        },
        async clearAdminUserSearch() {
            this.adminUserQuery = "";
            this.adminUserRoleFilter = "";
            this.adminUserActiveFilter = "";
            this.adminUserPage = 0;
            this.adminUserSize = 10;
            await this.fetchAdminUsers();
        },
        async goToPreviousAdminUserPage() {
            if (!this.adminUserHasPrevious || this.adminUserPage <= 0) {
                return;
            }
            this.adminUserPage -= 1;
            await this.fetchAdminUsers();
        },
        async goToNextAdminUserPage() {
            if (!this.adminUserHasNext) {
                return;
            }
            this.adminUserPage += 1;
            await this.fetchAdminUsers();
        },
        async changeAdminUserSize() {
            this.adminUserPage = 0;
            await this.fetchAdminUsers();
        },
        async searchAdminContributorApplications() {
            await this.fetchAdminContributorApplications();
        },
        async clearAdminContributorApplications() {
            this.adminContributorApplicationQuery = "";
            await this.fetchAdminContributorApplications();
        },
        clearMyContributorApplicationFilter() {
            this.myContributorApplicationStatusFilter = "";
        },
        async refreshWorkspaceData() {
            await Promise.all([
                this.fetchMyProfile(),
                this.fetchMyPosts(),
                this.fetchMyContributorApplications()
            ]);
            if (this.isAdmin) {
                await Promise.all([
                    this.fetchAdminPosts(),
                    this.fetchAdminUsers(),
                    this.fetchPendingQueue(),
                    this.fetchAdminContributorApplications()
                ]);
            }
            this.ensureAccessibleProfileSection();
        },
        async refreshAdminViews() {
            if (!this.isAdmin) {
                this.adminPosts = [];
                this.pendingQueuePosts = [];
                return;
            }
            await this.fetchPendingQueue();
            if (this.adminSection === "articles" || this.selectedAdminPostId) {
                await this.fetchAdminPosts();
            }
            if (this.adminSection === "contributor-review") {
                await this.fetchAdminContributorApplications();
            }
        },
        async openPost(postId) {
            this.socialCopyLoading = false;
            this.socialCopyResult = null;
            const postInCache = this.defaultHomepagePostsCache.find((post) => post.id === postId);
            if (postInCache) {
                postInCache.viewCount = Number(postInCache.viewCount || 0) + 1;
            }

            const postInList = this.posts.find((post) => post.id === postId);
            if (postInList) {
                postInList.viewCount = Number(postInList.viewCount || 0) + 1;
            }

            if (fallbackPostDetails[postId]) {
                fallbackPostDetails[postId].viewCount = Number(fallbackPostDetails[postId].viewCount || 0) + 1;
                this.selectedPost = normaliseDetail(clone(fallbackPostDetails[postId]));
                this.selectedPost.likedByCurrentUser = this.isPostLiked(postId);
                this.selectedPostId = postId;
                this.currentCommentPage = 1;
                this.currentView = "detail";
                if (window.location.hash !== `#detail-${postId}`) {
                    window.location.hash = `#detail-${postId}`;
                }
                return;
            }

            this.loading = true;
            this.errorMessage = "";
            try {
                const response = await fetch(`/api/posts/${postId}`, {
                    headers: this.currentUser ? this.authHeaders() : {}
                });
                const payload = await response.json();
                if (!payload.success) {
                    const requestError = new Error(this.translateBackendMessage(payload.message) || "Unable to load this publication.");
                    requestError.status = response.status;
                    throw requestError;
                }
                this.selectedPost = normaliseDetail(payload.data);
                this.setStoredLike(postId, Boolean(this.selectedPost.likedByCurrentUser));
                this.selectedPostId = postId;
                this.currentCommentPage = 1;
                this.currentView = "detail";
                if (this.isAdmin) {
                    await this.loadAiCoverCandidates(postId);
                }
                if (window.location.hash !== `#detail-${postId}`) {
                    window.location.hash = `#detail-${postId}`;
                }
            } catch (error) {
                if (error.status === 404) {
                    this.removePostFromPublicCaches(postId);
                    this.showError(error.message || "Unable to load this publication.");
                    return;
                }
                const summary = this.posts.find((post) => post.id === postId);
                if (summary) {
                    this.selectedPost = normaliseDetail({
                        ...summary,
                        likedByCurrentUser: this.isPostLiked(postId),
                        content: "This publication is available in the front-end preview, but the live detail endpoint is not currently returning a full payload.",
                        imageUrls: summary.imageUrls?.length ? summary.imageUrls : (summary.coverImageUrl ? [summary.coverImageUrl] : []),
                        comments: []
                    });
                    this.selectedPostId = postId;
                    this.currentCommentPage = 1;
                    this.currentView = "detail";
                    if (window.location.hash !== `#detail-${postId}`) {
                        window.location.hash = `#detail-${postId}`;
                    }
                } else {
                    this.showError(error.message || "Unable to load this publication.");
                }
            } finally {
                this.loading = false;
            }
        },
        async likePost() {
            if (!this.selectedPostId) {
                this.showError("Please open a publication before liking.");
                return;
            }
            if (this.likeRequestPending) {
                return;
            }
            if (!this.currentUser) {
                this.redirectGuestToRegister("Sign in before liking publications.");
                return;
            }

            const postId = this.selectedPostId;
            const previousLiked = this.selectedPostLiked;
            const previousLikeCount = Number(this.selectedPost?.likeCount || 0);
            const liked = !previousLiked;
            const optimisticLikeCount = Math.max(0, previousLikeCount + (liked ? 1 : -1));

            this.applyPostLikeState(postId, optimisticLikeCount, liked);
            this.likeRequestPending = true;
            if (fallbackPostDetails[postId]) {
                this.setStoredLike(postId, liked);
                this.showSuccess(liked ? "Publication liked successfully." : "Publication like removed.");
                this.likeRequestPending = false;
                return;
            }

            try {
                const data = await this.request(`/api/posts/${postId}/like`, {
                    method: "POST"
                }, "", null, true);

                if (data) {
                    this.selectedPost = normaliseDetail(data);
                    const liked = Boolean(this.selectedPost.likedByCurrentUser);
                    this.applyPostLikeState(postId, data.likeCount, liked);
                    this.setStoredLike(postId, liked);
                    this.showSuccess(liked ? "Publication liked successfully." : "Publication like removed.");
                } else {
                    this.applyPostLikeState(postId, previousLikeCount, previousLiked);
                }
            } finally {
                this.likeRequestPending = false;
            }
        },
        async inspectCategory(categoryName) {
            const resolvedCategoryName = this.resolveCategoryName(categoryName);
            const resolvedCategoryId = this.resolveCategoryId(resolvedCategoryName);
            this.selectedCategoryFocus = resolvedCategoryId;
            this.selectedCategoryFilter = resolvedCategoryName;
            this.resetPostPagination();
            this.navigate("home");
            await this.fetchPosts({
                showGlobalLoading: false,
                useHomepageSearchLoading: true
            });
            this.scrollToCollections();
        },
        async applyCategoryToSearch(categoryName) {
            const resolvedCategoryName = this.resolveCategoryName(categoryName);
            const resolvedCategoryId = this.resolveCategoryId(resolvedCategoryName);
            this.selectedCategoryFocus = resolvedCategoryId;
            this.selectedCategoryFilter = resolvedCategoryName;
            this.resetPostPagination();
            this.navigate("home");
            await this.fetchPosts({
                showGlobalLoading: false,
                useHomepageSearchLoading: true
            });
        },
        focusCollections(shouldNavigate = true) {
            if (shouldNavigate) {
                this.navigate("publish");
            }
            this.scrollToCollections();
        },
        scrollToCollections() {
            this.$nextTick(() => {
                const section = this.$refs.homeCollectionsSection;
                if (section?.scrollIntoView) {
                    section.scrollIntoView({ behavior: "smooth", block: "start" });
                }
            });
        },
        scrollToArticleResults() {
            this.$nextTick(() => {
                const section = this.$refs.articleResultsSection;
                if (section?.scrollIntoView) {
                    section.scrollIntoView({ behavior: "smooth", block: "start" });
                }
            });
        },
        focusAuthInput(refName) {
            this.$nextTick(() => {
                const input = this.$refs[refName];
                if (input?.focus) {
                    input.focus();
                }
            });
        },
        resetPasswordRecoveryForm() {
            this.passwordRecoveryLookupUsername = "";
            this.passwordRecoveryUsername = "";
            this.passwordRecoveryQuestions = [];
            this.passwordRecoveryAnswers = createEmptySecurityAnswers();
            this.passwordRecoveryNewPassword = "";
            this.passwordRecoveryConfirmPassword = "";
        },
        openRegisterMode() {
            this.authMode = "register";
            this.errorMessage = "";
            this.authBanner = "";
            this.focusAuthInput("registerUsernameInput");
        },
        openLoginMode() {
            this.authMode = "login";
            this.errorMessage = "";
            this.resetPasswordRecoveryForm();
            this.focusAuthInput("loginUsernameInput");
        },
        openPasswordRecoveryMode() {
            this.authMode = "recover";
            this.errorMessage = "";
            this.authBanner = "";
            this.resetPasswordRecoveryForm();
            this.focusAuthInput("passwordRecoveryUsernameInput");
        },
        clearHomeSearch() {
            this.homeSearchQuery = "";
            this.selectedCategoryFilter = "";
            this.resetPostPagination();
            this.applyHomepageSearchState("", "");
            this.syncHomepageSearchStateToUrl();
            this.errorMessage = "";
            return this.fetchPosts({
                allowFallback: true,
                showGlobalLoading: false,
                useHomepageSearchLoading: true
            });
        },
        async selectProfileSection(section) {
            if (!this.canAccessContributorWorkspace && ["published", "pending", "drafts"].includes(section)) {
                this.profileSection = "liked";
                return;
            }
            this.profileSection = section;
            if (this.currentUser) {
                await this.fetchMyProfile();
                await this.fetchMyPosts();
                await this.fetchMyContributorApplications();
            }
        },
        async selectAdminSection(section) {
            this.adminSection = section;
            if (section !== "articles") {
                this.pendingQueueReturnToApprovals = false;
                this.closeAdminPostPreview();
            }
            if (this.isAdmin && section === "articles") {
                await this.fetchAdminPosts();
            }
            if (this.isAdmin && section === "users") {
                await this.fetchAdminUsers();
            }
            if (this.isAdmin && section === "contributor-review") {
                await this.fetchAdminContributorApplications();
            }
            if (this.isAdmin && section === "approvals") {
                await this.fetchPendingQueue();
            }
        },
        resolveCategoryId(categoryName) {
            const value = categoryName == null ? "" : String(categoryName);
            const match = this.categories.find((category) => category.name === value || String(category.id) === value)
                || this.categoryStats.find((category) => category.name === value || String(category.id) === value);
            return match ? String(match.id) : "";
        },
        resolveCategoryName(categoryValue) {
            const value = categoryValue == null ? "" : String(categoryValue).trim();
            if (!value) {
                return "";
            }

            const match = this.categories.find((category) => category.name === value || String(category.id) === value)
                || this.categoryStats.find((category) => category.name === value || String(category.id) === value);
            return match?.name || value;
        },
        storeDraftSnapshot(post) {
            if (!post?.id) {
                return;
            }
            this.draftCache[String(post.id)] = post;
            localStorage.setItem(draftCacheKey, JSON.stringify(this.draftCache));
        },
        removeDraftSnapshot(postId) {
            if (!postId) {
                return;
            }
            delete this.draftCache[String(postId)];
            localStorage.setItem(draftCacheKey, JSON.stringify(this.draftCache));
        },
        resetPostEditor() {
            this.postForm = createEmptyPostForm();
            this.editingPostId = null;
            this.editingPostStatus = "";
            this.editingRejectReason = "";
            this.socialCopyLoading = false;
            this.socialCopyResult = null;
            this.coverFileLabel = "No file selected";
            this.galleryFileLabel = "No files selected";
            this.pdfFileLabel = "No PDF selected";
        },
        applyPostDetailToEditor(postDetail, summaryEntry = null) {
            this.socialCopyLoading = false;
            this.socialCopyResult = null;
            const fallbackEntry = summaryEntry || {};
            const resolvedCategoryId = postDetail.categoryId
                ? String(postDetail.categoryId)
                : this.resolveCategoryId(postDetail.categoryName || fallbackEntry.categoryName || "");

            this.editingPostId = postDetail.id || fallbackEntry.id || null;
            this.editingPostStatus = postDetail.status || fallbackEntry.status || "";
            this.editingRejectReason = postDetail.rejectReason || fallbackEntry.rejectReason || "";
            this.postForm = {
                participantId: postDetail.participantId || fallbackEntry.participantId || "",
                title: postDetail.title || fallbackEntry.title || "",
                content: postDetail.content || postDetail.abstractText || fallbackEntry.content || "",
                categoryId: resolvedCategoryId,
                coverImageUrl: postDetail.coverImageUrl || fallbackEntry.coverImageUrl || "",
                heritageName: postDetail.heritageName || fallbackEntry.heritageName || "",
                region: postDetail.region || fallbackEntry.region || "",
                imageUrls: Array.isArray(postDetail.imageUrls)
                    ? [...postDetail.imageUrls]
                    : Array.isArray(fallbackEntry.imageUrls)
                        ? [...fallbackEntry.imageUrls]
                        : [],
                publication: true,
                publicationAuthors: postDetail.publicationAuthors || fallbackEntry.publicationAuthors || "",
                publicationYear: postDetail.publicationYear || fallbackEntry.publicationYear || "",
                venue: postDetail.venue || fallbackEntry.venue || "",
                abstractText: postDetail.abstractText || fallbackEntry.abstractText || "",
                keywords: postDetail.keywords || fallbackEntry.keywords || "",
                doi: postDetail.doi || fallbackEntry.doi || "",
                bibtex: postDetail.bibtex || fallbackEntry.bibtex || "",
                researchArea: postDetail.researchArea || fallbackEntry.researchArea || "",
                pdfUrl: postDetail.pdfUrl || fallbackEntry.pdfUrl || "",
                codeUrl: postDetail.codeUrl || fallbackEntry.codeUrl || "",
                datasetUrl: postDetail.datasetUrl || fallbackEntry.datasetUrl || ""
            };
            this.coverFileLabel = this.postForm.coverImageUrl ? this.postForm.coverImageUrl.split("/").pop() : "No file selected";
            this.galleryFileLabel = this.postForm.imageUrls.length
                ? `${this.postForm.imageUrls.length} files selected`
                : "No files selected";
            this.pdfFileLabel = this.postForm.pdfUrl ? this.postForm.pdfUrl.split("/").pop() : "No PDF selected";
            this.aiCoverPreviewMode = "website";
            this.navigate("publish");
            if (this.isAdmin && this.editingPostId) {
                this.loadAiCoverCandidates(this.editingPostId);
            }
        },
        async editPost(entry) {
            const cached = this.draftCache[String(entry.id)] || null;
            const data = await this.request(`/api/my/posts/${entry.id}`, {
                method: "GET"
            }, "", null, true);

            if (data) {
                this.applyPostDetailToEditor(data, entry);
                this.storeDraftSnapshot({
                    ...data,
                    categoryId: data.categoryId || this.resolveCategoryId(data.categoryName || entry.categoryName || ""),
                    rejectReason: entry.rejectReason || ""
                });
                return;
            }

            if (cached) {
                this.showSuccess("Using the latest local draft snapshot because the live draft detail could not be loaded.");
                this.applyPostDetailToEditor(cached, entry);
                return;
            }

            this.showError("This publication could not be re-opened because the live draft detail is unavailable and no local draft snapshot was found.");
        },
        async returnPostToDraft(entry) {
            if (!entry?.id) {
                return;
            }
            if (entry.status === "DRAFT" || entry.status === "REJECTED") {
                await this.editPost(entry);
                return;
            }

            const postId = entry.id;
            const data = await this.request(`/api/my/posts/${postId}/return-to-draft`, {
                method: "POST"
            }, "Publication moved back to draft. Please edit and submit it for review again.", null, true);
            if (!data) {
                return;
            }

            this.removePostFromPublicCaches(postId);
            this.storeDraftSnapshot({
                ...data,
                categoryId: data.categoryId || this.resolveCategoryId(data.categoryName || entry.categoryName || ""),
                rejectReason: ""
            });
            await this.fetchMyPosts();
            await this.fetchPosts({ showGlobalLoading: false });
            this.applyPostDetailToEditor(data, entry);
        },
        async deleteMyPost(entry) {
            if (!entry?.id) {
                return;
            }

            const postId = entry.id;
            let deleted = false;
            await this.request(`/api/my/posts/${postId}`, {
                method: "DELETE"
            }, "Publication archived successfully.", () => {
                deleted = true;
            }, true);

            if (!deleted) {
                return;
            }
            this.removePostFromPublicCaches(postId);
            this.removeDraftSnapshot(postId);
            if (this.editingPostId === postId) {
                this.resetPostEditor();
            }
            await this.fetchMyPosts();
            await this.fetchPosts({ showGlobalLoading: false });
        },
        async openAdminPost(postId) {
            this.selectedAdminPostId = postId;
            this.aiContentGenerationLoading = false;
            this.aiContentGenerationStage = "";
            this.socialCopyResult = null;
            this.aiCoverGenerationResult = null;
            this.aiCoverCandidates = [];
            this.aiCoverPreviewMode = "website";
            const data = await this.request(`/api/admin/posts/${postId}`, {
                method: "GET"
            }, "", null, true);
            if (data) {
                this.selectedAdminPost = data;
                this.adminReviewReason = data.rejectReason || "";
                await this.loadAiCoverCandidates(postId);
            }
        },
        async openAdminPostPreview(postId) {
            const data = await this.request(`/api/admin/posts/${postId}`, {
                method: "GET"
            }, "", null, true);
            if (data) {
                this.adminPreviewPostId = postId;
                this.adminPreviewPost = data;
                this.syncBodyScrollLock();
            }
        },
        closeAdminPostPreview() {
            this.closeImagePreview();
            this.adminPreviewPostId = null;
            this.adminPreviewPost = null;
            this.syncBodyScrollLock();
        },
        async submitPermissionRequest() {
            console.log("=== submitPermissionRequest called ===");
            console.log("currentUser:", this.currentUser);
            console.log("applicationReason:", this.contributorApplicationForm.applicationReason);
            console.log("attachment:", this.contributorApplicationForm.attachment);
            
            if (!this.currentUser) {
                console.error("User not logged in!");
                this.showError("Please sign in again before continuing.");
                return;
            }
            
            if (!this.contributorApplicationForm.applicationReason.trim()) {
                this.showError("Application reason cannot be empty.");
                return;
            }

            const formData = new FormData();
            formData.append("request", JSON.stringify({
                applicationReason: this.contributorApplicationForm.applicationReason
            }));
            if (this.contributorApplicationForm.attachment) {
                formData.append("attachment", this.contributorApplicationForm.attachment);
            }

            console.log("Sending request to /api/my/contributor-applications");
            const data = await this.request("/api/my/contributor-applications", {
                method: "POST",
                body: formData
            }, "Contributor application submitted successfully.", null, true);
            console.log("Response data:", data);
            if (data) {
                await this.fetchMyContributorApplications();
                this.profileSection = "permissions";
                this.contributorApplicationForm = {
                    applicationReason: "",
                    attachment: null
                };
                this.attachmentFileLabel = "No file selected";
            }
        },
        handleAttachmentChange(event) {
            const file = event.target.files[0];
            if (file) {
                if (file.type !== "application/pdf") {
                    this.showError("Only PDF attachments are supported.");
                    this.contributorApplicationForm.attachment = null;
                    this.attachmentFileLabel = "No file selected";
                    event.target.value = "";
                    return;
                }
                this.contributorApplicationForm.attachment = file;
                this.attachmentFileLabel = file.name;
            } else {
                this.contributorApplicationForm.attachment = null;
                this.attachmentFileLabel = "No file selected";
            }
        },
        async register() {
            const validationMessage = this.validateRegisterForm();
            if (validationMessage) {
                this.showError(validationMessage);
                return;
            }

            const payload = {
                username: this.registerForm.username.trim(),
                nickname: this.registerForm.nickname.trim(),
                email: this.normaliseOptionalField(this.registerForm.email),
                phone: this.normaliseOptionalField(this.registerForm.phone),
                password: this.registerForm.password,
                securityQuestions: this.registerForm.securityQuestions.map((question) => ({
                    questionText: question.questionText,
                    answer: question.answer
                }))
            };

            await this.request("/api/auth/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            }, "Registration complete. Please sign in.", async () => {
                this.authBanner = "Registration complete. Please sign in.";
                this.loginForm = {
                    username: payload.username,
                    password: ""
                };
                this.registerForm = createEmptyRegisterForm();
                this.authMode = "login";
                this.navigate("auth");
                this.focusAuthInput("loginUsernameInput");
            });
        },
        async login() {
            const validationMessage = this.validateLoginForm();
            if (validationMessage) {
                this.showError(validationMessage);
                return;
            }

            await this.request("/api/auth/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(this.loginForm)
            }, "Signed in successfully.", async (data) => {
                this.currentUser = data;
                this.storeUser(data);
                this.syncProfileFormFromCurrentUser();
                this.syncLikedPostsFromStorage();
                this.ensureAccessibleProfileSection();
                this.authBanner = "";
                this.loginForm = { username: "", password: "" };
                await this.refreshWorkspaceData();
                this.navigate("home");
            });
        },
        async loadPasswordRecoveryQuestions() {
            const username = String(this.passwordRecoveryLookupUsername || "").trim();
            if (!username) {
                this.showError("Username cannot be empty.");
                return;
            }

            const data = await this.request("/api/auth/password-recovery/questions", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username })
            });

            if (Array.isArray(data) && data.length === requiredSecurityQuestionCount) {
                this.passwordRecoveryUsername = username;
                this.passwordRecoveryQuestions = data;
                this.passwordRecoveryAnswers = createEmptySecurityAnswers();
                this.passwordRecoveryNewPassword = "";
                this.passwordRecoveryConfirmPassword = "";
                this.showSuccess("Security questions loaded. Please answer at least 2 correctly to reset your password.");
            }
        },
        async resetPasswordBySecurityQuestions() {
            const validationMessage = this.validatePasswordRecoveryForm();
            if (validationMessage) {
                this.showError(validationMessage);
                return;
            }

            const payload = {
                username: this.passwordRecoveryUsername,
                newPassword: this.passwordRecoveryNewPassword,
                answers: this.passwordRecoveryAnswers
            };

            await this.request("/api/auth/password-recovery/reset", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            }, "Password reset successfully. Please sign in with your new password.", async () => {
                this.authMode = "login";
                this.authBanner = "Password reset successfully. Please sign in with your new password.";
                this.loginForm = {
                    username: this.passwordRecoveryUsername,
                    password: ""
                };
                this.resetPasswordRecoveryForm();
                this.focusAuthInput("loginUsernameInput");
            });
        },
        async changeMyPasswordFromProfile() {
            if (!this.currentUser) {
                this.redirectGuestToRegister("Sign in before changing your password.");
                return;
            }
            const validationMessage = this.validateProfilePasswordChangeForm();
            if (validationMessage) {
                this.showError(validationMessage);
                return;
            }
            await this.request("/api/my/profile/password/change", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    newPassword: this.passwordChangeNewPassword,
                    answers: this.passwordChangeAnswers
                })
            }, "Password changed successfully.", async () => {
                this.resetPasswordChangeForm();
            }, true);
        },
        logout() {
            this.currentUser = null;
            this.selectedPostId = null;
            this.selectedPost = null;
            this.selectedAdminPostId = null;
            this.selectedAdminPost = null;
            this.aiCoverGenerationLoading = false;
            this.aiCoverGenerationResult = null;
            this.aiCoverCandidates = [];
            this.aiCoverSelectionLoading = false;
            this.deepseekApiKeyInput = "";
            this.qwenApiKeyInput = "";
            this.deepseekKeyStatus = { configured: false, source: "none", message: "Sign in to configure" };
            this.qwenKeyStatus = { configured: false, source: "none", message: "Sign in to configure" };
            this.aiContentGenerationLoading = false;
            this.aiContentGenerationStage = "";
            this.socialCopyLoading = false;
            this.socialCopyResult = null;
            this.socialCopyLanguage = this.siteLanguage;
            this.socialCopyMode = "outreach";
            this.socialCopyTone = "engaging";
            this.closeAdminPostPreview();
            this.profileSection = "published";
            this.profileEditingMode = "";
            this.profileEditTab = "profile";
            this.adminSection = "articles";
            this.pendingQueueQuery = "";
            this.pendingQueueReturnToApprovals = false;
            this.myPosts = [];
            this.contributorApplications = [];
            this.adminPosts = [];
            this.adminUsers = [];
            this.adminUserPage = 0;
            this.adminUserSize = 10;
            this.adminUserTotalElements = 0;
            this.adminUserTotalPages = 0;
            this.adminUserHasPrevious = false;
            this.adminUserHasNext = false;
            this.pendingQueuePosts = [];
            this.adminContributorApplications = [];
            this.workspaceDrafts = [];
            this.workspacePending = [];
            this.likedPostIds = [];
            this.authBanner = "";
            this.resetPostEditor();
            this.resetPasswordRecoveryForm();
            this.resetPasswordChangeForm();
            localStorage.removeItem("heritage-current-user");
            this.navigate("auth");
            this.showSuccess("You have been signed out.");
        },
        async saveProfile() {
            if (!this.currentUser) {
                this.redirectGuestToRegister("Sign in before viewing your profile.");
                return;
            }

            const nickname = String(this.profileForm.nickname || "").trim();
            if (!nickname) {
                this.showError("Display name cannot be empty.");
                return;
            }

            if (this.profileBioByteCount > 500) {
                this.showError("Personal bio cannot exceed 500 bytes.");
                return;
            }

            const data = await this.request("/api/my/profile", {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    nickname,
                    avatarUrl: this.normaliseOptionalField(this.profileForm.avatarUrl),
                    bio: this.normaliseOptionalField(this.profileForm.bio)
                })
            }, "Profile updated successfully.", null, true);

            if (data) {
                this.mergeCurrentUserProfile(data);
                this.closeProfileEditor();
            }
        },
        clearProfileAvatar() {
            this.profileForm.avatarUrl = "";
            this.profileAvatarFileLabel = "No file selected";
        },
        async handleProfileAvatarUpload(event) {
            const file = event.target.files[0];
            if (!file) {
                this.profileAvatarFileLabel = "No file selected";
                return;
            }

            this.profileAvatarFileLabel = file.name;
            const result = await this.uploadProfileAvatar(file);
            if (result) {
                this.profileForm.avatarUrl = result.url;
                this.currentUser = {
                    ...this.currentUser,
                    avatarUrl: result.url
                };
                this.storeUser(this.currentUser);
                this.profileEditingMode = "profile";
                this.profileEditTab = "profile";
            }
            event.target.value = "";
        },
        async submitPost() {
            if (!this.currentUser) {
                this.showError("Please sign in before saving a draft.");
                this.navigate("auth");
                return null;
            }
            const postValidationError = this.validatePostForm();
            if (postValidationError) {
                this.showError(postValidationError);
                return null;
            }

            const title = String(this.postForm.title || "").trim();
            const abstractText = String(this.postForm.abstractText || "").trim();
            const payload = {
                ...this.postForm,
                title,
                content: abstractText || title,
                categoryId: this.postForm.categoryId ? Number(this.postForm.categoryId) : null,
                heritageName: title,
                region: "Academic Publication",
                publication: true,
                publicationYear: this.postForm.publicationYear
                    ? Number(this.postForm.publicationYear)
                    : null
            };

            const url = this.editingPostId ? `/api/posts/${this.editingPostId}` : "/api/posts";
            const method = this.editingPostId ? "PUT" : "POST";
            const successMessage = this.isAdmin
                ? (this.editingPostId ? "Publication updated successfully." : "Publication published successfully.")
                : (this.editingPostId ? "Publication draft updated successfully." : "Publication draft saved successfully.");
            const data = await this.request(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            }, successMessage, null, true);

            if (data) {
                const publishedImmediately = data.status === "PUBLISHED";
                this.editingPostId = data.id;
                this.editingPostStatus = data.status;
                if (publishedImmediately) {
                    this.removeDraftSnapshot(data.id);
                    await this.fetchPosts();
                    if (this.isAdmin) {
                        await this.fetchAdminPosts();
                    }
                } else {
                    this.storeDraftSnapshot(data);
                }
                await this.fetchMyPosts();
                this.profileSection = publishedImmediately ? "published" : "drafts";
                this.aiCoverPreviewMode = "website";
                await this.loadAiCoverCandidates(data.id);
            }
            return data || null;
        },
        async submitCurrentPostForReview() {
            if (!this.canSubmitForReview) {
                return;
            }
            const reviewValidationError = this.validatePublicationReadyForReview();
            if (reviewValidationError) {
                this.showError(reviewValidationError);
                return;
            }
            const data = await this.request(`/api/posts/${this.editingPostId}/submit-review`, {
                method: "POST"
            }, "Publication submitted for review.", null, true);

            if (data) {
                this.removeDraftSnapshot(this.editingPostId);
                this.resetPostEditor();
                await this.fetchMyPosts();
                this.profileSection = "pending";
                this.navigate("publish");
            }
        },
        triggerFilePicker(refName) {
            const input = this.$refs[refName];
            if (input) {
                input.click();
            }
        },
        async submitComment() {
            if (!this.currentUser) {
                this.showError("Please sign in before posting a comment.");
                this.navigate("auth");
                return;
            }
            if (!this.selectedPostId) {
                this.showError("Please open a publication before posting a comment.");
                return;
            }

            const content = String(this.commentForm.content || "").trim();
            if (!content) {
                this.showError("Comment content cannot be empty.");
                return;
            }

            const postId = this.selectedPostId;
            const data = await this.request(`/api/posts/${postId}/comments`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    content
                })
            }, "Comment posted successfully.", null, true);

            if (data) {
                this.commentForm.content = "";
                if (!Array.isArray(this.selectedPost.comments)) {
                    this.selectedPost.comments = [];
                }
                this.selectedPost.comments = [
                    data,
                    ...this.selectedPost.comments.filter((comment) => String(comment.id) !== String(data.id))
                ];
                this.applyPostCommentCount(postId, Number(this.selectedPost.commentCount || 0) + 1);
                this.currentCommentPage = 1;
            }
        },
        async deleteComment(comment) {
            if (!this.currentUser) {
                this.showError("Please sign in before deleting a comment.");
                this.navigate("auth");
                return;
            }
            if (!this.selectedPostId || !comment?.id) {
                this.showError("Please open a publication before deleting a comment.");
                return;
            }
            if (!this.canDeleteComment(comment)) {
                this.showError("You can only delete your own comments.");
                return;
            }

            const postId = this.selectedPostId;
            let deleted = false;
            await this.request(`/api/posts/${postId}/comments/${comment.id}`, {
                method: "DELETE"
            }, "Comment deleted successfully.", () => {
                deleted = true;
            }, true);

            if (deleted && this.selectedPost) {
                const comments = Array.isArray(this.selectedPost.comments) ? this.selectedPost.comments : [];
                this.selectedPost.comments = comments.filter((item) => String(item.id) !== String(comment.id));
                this.applyPostCommentCount(postId, Number(this.selectedPost.commentCount || 0) - 1);
                this.currentCommentPage = Math.min(this.currentCommentPage, this.totalCommentPages);
            }
        },
        previousCommentPage() {
            this.currentCommentPage = Math.max(1, this.currentCommentPage - 1);
        },
        nextCommentPage() {
            this.currentCommentPage = Math.min(this.totalCommentPages, this.currentCommentPage + 1);
        },
        async handleCoverUpload(event) {
            const file = event.target.files[0];
            if (!file) {
                this.coverFileLabel = "No file selected";
                return;
            }
            this.coverFileLabel = file.name;
            const result = await this.uploadImage(file);
            if (result) {
                this.postForm.coverImageUrl = result.url;
            }
            event.target.value = "";
        },
        async handleGalleryUpload(event) {
            const files = Array.from(event.target.files || []);
            if (!files.length) {
                this.galleryFileLabel = "No files selected";
                return;
            }
            this.galleryFileLabel = files.length === 1 ? files[0].name : `${files.length} files selected`;
            for (const file of files) {
                const result = await this.uploadImage(file);
                if (result) {
                    this.postForm.imageUrls.push(result.url);
                }
            }
            event.target.value = "";
        },
        async handlePdfUpload(event) {
            const file = event.target.files[0];
            if (!file) {
                this.pdfFileLabel = "No PDF selected";
                return;
            }
            this.errorMessage = "";
            this.pdfUploadLoading = true;
            this.pdfUploadProgress = 0;
            this.pdfFileLabel = file.name;
            try {
                const result = await this.uploadPdf(file);
                if (result) {
                    this.postForm.pdfUrl = result.url;
                    this.postForm.publication = true;
                    this.postForm.coverImageUrl = "";
                    this.coverFileLabel = "No file selected";
                }
            } finally {
                this.pdfUploadLoading = false;
            }
            event.target.value = "";
        },
        resolveAiCoverTarget() {
            if (this.currentView === "publish" && this.editingPostId) {
                return {
                    postId: this.editingPostId,
                    post: { ...this.postForm, id: this.editingPostId }
                };
            }
            if (this.currentView === "detail" && this.selectedPost) {
                return {
                    postId: this.selectedPostId || this.selectedPost.id,
                    post: this.selectedPost
                };
            }
            return {
                postId: this.selectedAdminPostId,
                post: this.selectedAdminPost
            };
        },
        setSocialCopyLanguage(language) {
            const nextLanguage = language === "en" ? "en" : "zh";
            if (this.socialCopyLanguage !== nextLanguage) {
                this.socialCopyLanguage = nextLanguage;
                this.socialCopyResult = null;
            }
        },
        setSocialCopyMode(mode) {
            const nextMode = mode === "outreach" ? "outreach" : "personal";
            if (this.socialCopyMode !== nextMode) {
                this.socialCopyMode = nextMode;
                this.socialCopyResult = null;
            }
        },
        selectSocialCopyTone() {
            const variant = this.socialCopyResult?.variants?.[this.socialCopyTone];
            if (variant) {
                this.socialCopyResult = {
                    ...this.socialCopyResult,
                    tone: this.socialCopyTone,
                    copyText: variant
                };
            }
        },
        async generateSocialCopyForPost(post, notify = true) {
            if (!post?.id || !post.pdfUrl) {
                this.showError(this.siteLanguage === "zh" ? "请先上传 PDF 再生成文案。" : "Upload a PDF before generating social copy.");
                return null;
            }
            this.socialCopyLoading = true;
            const data = await this.request(`/api/publications/${post.id}/social-copy/generate`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    language: this.siteLanguage,
                    tone: this.socialCopyTone,
                    audience: this.socialCopyAudience,
                    goal: this.socialCopyGoal,
                    length: this.socialCopyLength,
                    callToAction: this.socialCopyCallToAction,
                    mode: this.socialCopyMode
                })
            }, "", null, true);
            this.socialCopyLoading = false;
            if (!data) {
                return null;
            }
            this.socialCopyResult = data;
            this.socialCopyTone = data.tone || this.socialCopyTone;
            if (notify) {
                this.showSuccess(this.socialCopySourceLabel(data.source));
            }
            return data;
        },
        async generateSocialCopy() {
            if (!this.canGenerateSocialCopy || !this.selectedPost?.id) {
                this.showError("Only the publication uploader or an administrator can generate social copy.");
                return null;
            }
            return this.generateSocialCopyForPost(this.selectedPost, true);
        },
        async generateSocialCopyForPublishPost() {
            if (!this.postForm?.pdfUrl) {
                this.showError(this.siteLanguage === "zh" ? "请先上传 PDF 再生成文案。" : "Upload a PDF before generating social copy.");
                return null;
            }
            const savedPost = await this.submitPost();
            if (!savedPost) {
                return null;
            }
            return this.generateSocialCopyForPost({ ...this.postForm, id: savedPost.id }, true);
        },
        async generateSocialCopyForAdminPost() {
            if (!this.isAdmin || !this.selectedAdminPost?.id) {
                this.showError("Open a publication in the admin workspace first.");
                return null;
            }
            return this.generateSocialCopyForPost(this.selectedAdminPost, true);
        },
        selectSocialCopyCandidate(candidate) {
            if (!candidate?.copyText) {
                return;
            }
            this.socialCopyResult = {
                ...this.socialCopyResult,
                tone: candidate.style || this.socialCopyTone,
                copyText: candidate.copyText,
                claimChecks: candidate.claimChecks || []
            };
            this.socialCopyTone = candidate.style || this.socialCopyTone;
        },
        async copyGeneratedSocialCopy() {
            const copyText = this.socialCopyResult?.copyText;
            if (!copyText) {
                this.showError(this.siteLanguage === "zh" ? "请先生成文案再复制。" : "Generate social copy before copying it.");
                return;
            }
            try {
                await navigator.clipboard.writeText(copyText);
                this.showSuccess(this.siteLanguage === "zh" ? "文案已复制。" : "Social copy copied.");
            } catch (error) {
                this.showError(this.siteLanguage === "zh" ? "当前浏览器无法复制文案。" : "Unable to copy social copy in this browser.");
            }
        },
        socialCopySourceLabel(source) {
            if (source && source.includes("DEEPSEEK")) {
                return this.siteLanguage === "zh"
                    ? "已由 DeepSeek 基于论文证据生成发布文案，请发布前人工检查。"
                    : "DeepSeek generated this copy from paper evidence. Review it before publishing.";
            }
            return this.siteLanguage === "zh"
                ? "文案已由所选文字供应商基于论文证据生成。"
                : "The selected text provider generated this copy from paper evidence.";
        },
        syncTextProviderModel() {
            this.textProviderModelInput = { deepseek: "deepseek-v4-flash", openai: "gpt-4.1-mini", doubao: "doubao-seed-1-6-250615" }[this.textProviderInput] || "";
        },
        applySiteLanguage(language, persist = true) {
            const nextLanguage = language === "en" ? "en" : "zh";
            this.siteLanguage = nextLanguage;
            this.socialCopyLanguage = nextLanguage;
            this.imageStudioLanguage = nextLanguage;
            document.documentElement.lang = nextLanguage === "zh" ? "zh-CN" : "en";
            if (persist) {
                localStorage.setItem(siteLanguageStorageKey, nextLanguage);
                this.socialCopyResult = null;
            }
        },
        syncImageProviderModel() {
            this.imageProviderModelInput = { qwen: "qwen-image-2.0-pro", openai: "gpt-image-1.5", doubao: "doubao-seedream-4-5-251128", comfyui: "local-workflow" }[this.imageProviderInput] || "";
            this.imageProviderCustomModelInput = "";
        },
        withCurrentModel(models, current) {
            const values = [...models];
            if (current && !values.includes(current)) values.push(current);
            return values;
        },
        async loadProviderSettings() {
            if (!this.currentUser) return;
            const data = await this.request("/api/my/ai-settings/providers", { method: "GET" }, "", null, true);
            if (data) {
                this.providerSettings = data;
                this.textProviderInput = data.textProvider || "deepseek";
                this.textProviderModelInput = data.textModel || "";
                this.imageProviderInput = data.imageProvider || "qwen";
                const configuredImageModel = data.imageModel || "";
                const knownImageModels = this.imageProviderModelOptions;
                if (this.imageProviderInput === "doubao" && configuredImageModel && !knownImageModels.includes(configuredImageModel)) {
                    this.imageProviderModelInput = "custom-ark-endpoint";
                    this.imageProviderCustomModelInput = configuredImageModel;
                } else {
                    this.imageProviderModelInput = configuredImageModel;
                    this.imageProviderCustomModelInput = "";
                }
            }
        },
        async saveTextProvider() {
            this.providerSettingsSaving = true;
            const data = await this.request("/api/my/ai-settings/providers/text", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ provider: this.textProviderInput, apiKey: this.textProviderApiKeyInput.trim(), model: this.textProviderModelInput.trim() }) }, this.siteLanguage === "zh" ? "文字供应商已保存。" : "Text provider saved.", null, true);
            this.providerSettingsSaving = false;
            this.textProviderApiKeyInput = "";
            if (data) this.providerSettings = data;
        },
        async saveImageProvider() {
            this.providerSettingsSaving = true;
            const selectedImageModel = this.imageProviderModelInput === "custom-ark-endpoint"
                ? this.imageProviderCustomModelInput.trim()
                : this.imageProviderModelInput.trim();
            if (this.imageProviderInput === "doubao" && !selectedImageModel) {
                this.showError("Enter the Ark endpoint ID for the selected Seedream model.");
                return;
            }
            const data = await this.request("/api/my/ai-settings/providers/image", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ provider: this.imageProviderInput, apiKey: this.imageProviderApiKeyInput.trim(), model: selectedImageModel }) }, this.siteLanguage === "zh" ? "图片供应商已保存。" : "Image provider saved.", null, true);
            this.providerSettingsSaving = false;
            this.imageProviderApiKeyInput = "";
            if (data) this.providerSettings = data;
        },
        async clearProvider(type) {
            this.providerSettingsSaving = true;
            const data = await this.request(`/api/my/ai-settings/providers?type=${type}`, { method: "DELETE" }, "Provider key cleared.", null, true);
            this.providerSettingsSaving = false;
            if (data) { this.providerSettings = data; this.loadProviderSettings(); }
        },
        async loadDeepSeekKeyStatus() {
            if (!this.currentUser) {
                return;
            }
            const data = await this.request("/api/my/ai-settings/deepseek", {
                method: "GET"
            }, "", null, true);
            if (data) {
                this.deepseekKeyStatus = data;
            }
        },
        async saveDeepSeekApiKey() {
            const apiKey = String(this.deepseekApiKeyInput || "").trim();
            if (!apiKey) {
                this.showError("Enter a DeepSeek API key first.");
                return;
            }
            this.deepseekKeySaving = true;
            const data = await this.request("/api/my/ai-settings/deepseek", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ apiKey })
            }, "Your DeepSeek API key is active for this server session.", null, true);
            this.deepseekKeySaving = false;
            this.deepseekApiKeyInput = "";
            if (data) {
                this.deepseekKeyStatus = data;
            }
        },
        async clearDeepSeekApiKey() {
            this.deepseekKeySaving = true;
            const data = await this.request("/api/my/ai-settings/deepseek", {
                method: "DELETE"
            }, "Session-only DeepSeek API key cleared.", null, true);
            this.deepseekKeySaving = false;
            this.deepseekApiKeyInput = "";
            if (data) {
                this.deepseekKeyStatus = data;
            }
        },        async loadQwenKeyStatus() {
            if (!this.currentUser) {
                return;
            }
            const data = await this.request("/api/my/ai-settings/qwen", {
                method: "GET"
            }, "", null, true);
            if (data) {
                this.qwenKeyStatus = data;
            }
        },
        async saveQwenApiKey() {
            const apiKey = String(this.qwenApiKeyInput || "").trim();
            if (!apiKey) {
                this.showError("Enter a Qwen API key first.");
                return;
            }
            this.qwenKeySaving = true;
            const data = await this.request("/api/my/ai-settings/qwen", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ apiKey })
            }, "Your Qwen API key is active for this server session.", null, true);
            this.qwenKeySaving = false;
            this.qwenApiKeyInput = "";
            if (data) {
                this.qwenKeyStatus = data;
            }
        },
        async clearQwenApiKey() {
            this.qwenKeySaving = true;
            const data = await this.request("/api/my/ai-settings/qwen", {
                method: "DELETE"
            }, "Session-only Qwen API key cleared.", null, true);
            this.qwenKeySaving = false;
            this.qwenApiKeyInput = "";
            if (data) {
                this.qwenKeyStatus = data;
            }
        },
        setImageVariantMode(mode) {
            if (!["smart", "cover", "contain", "stretch"].includes(mode)) return;
            const enteringStretch = mode === "stretch" && this.imageVariantForm.mode !== "stretch";
            this.imageVariantForm.mode = mode;
            if (enteringStretch) this.fitImageStretchBoxToOutput();
        },
        setImageVariantPreset(width, height) {
            this.imageVariantForm.width = width;
            this.imageVariantForm.height = height;
            if (this.imageVariantForm.mode === "stretch") this.fitImageStretchBoxToOutput();
        },
        resetImageVariantStretchBox() {
            this.fitImageStretchBoxToOutput();
        },
        fitImageStretchBoxToOutput() {
            const width = Math.max(1, Number(this.imageVariantForm.width) || 900);
            const height = Math.max(1, Number(this.imageVariantForm.height) || 600);
            const stageAspect = 3 / 2;
            const percentRatio = (width / height) / stageAspect;
            const maxSize = 78;
            let boxWidth;
            let boxHeight;
            if (percentRatio >= 1) {
                boxWidth = maxSize;
                boxHeight = maxSize / percentRatio;
            } else {
                boxHeight = maxSize;
                boxWidth = maxSize * percentRatio;
            }
            this.imageStretchBox = {
                x: Math.round(((100 - boxWidth) / 2) * 10) / 10,
                y: Math.round(((100 - boxHeight) / 2) * 10) / 10,
                width: Math.round(boxWidth * 10) / 10,
                height: Math.round(boxHeight * 10) / 10
            };
        },
        isImageVariantPreset(width, height) {
            return Number(this.imageVariantForm.width) === width
                && Number(this.imageVariantForm.height) === height;
        },
        resetImageVariantCrop() {
            this.imageVariantForm.cropX = 0;
            this.imageVariantForm.cropY = 0;
            this.imageVariantForm.cropWidth = 100;
            this.imageVariantForm.cropHeight = 100;
        },
        captureImageVariantDimensions(event) {
            const image = event?.target;
            if (!image) return;
            this.imageNaturalWidth = image.naturalWidth || 900;
            this.imageNaturalHeight = image.naturalHeight || 600;
        },
        beginImageVariantFocus(event) {
            if (this.imageVariantForm.mode !== "smart") return;
            this.imageVariantDragging = true;
            this.updateImageVariantFocus(event);
            window.addEventListener("pointermove", this.updateImageVariantFocus);
            window.addEventListener("pointerup", this.endImageVariantFocus);
        },
        updateImageVariantFocus(event) {
            if (!this.imageVariantDragging) return;
            const stage = this.$refs.imageVariantStage;
            if (!stage) return;
            const rect = stage.getBoundingClientRect();
            this.imageVariantForm.focusX = Math.round(Math.max(0, Math.min(100, ((event.clientX - rect.left) / rect.width) * 100)));
            this.imageVariantForm.focusY = Math.round(Math.max(0, Math.min(100, ((event.clientY - rect.top) / rect.height) * 100)));
        },
        endImageVariantFocus() {
            this.imageVariantDragging = false;
            window.removeEventListener("pointermove", this.updateImageVariantFocus);
            window.removeEventListener("pointerup", this.endImageVariantFocus);
        },
        beginImageCropInteraction(event, action) {
            if (this.imageVariantForm.mode !== "cover") return;
            event.preventDefault();
            event.stopPropagation();
            const stage = this.$refs.imageVariantStage;
            if (!stage) return;
            this.imageEditorInteraction = {
                kind: "crop", action,
                startClientX: event.clientX, startClientY: event.clientY,
                stageRect: stage.getBoundingClientRect(),
                cropX: Number(this.imageVariantForm.cropX), cropY: Number(this.imageVariantForm.cropY),
                cropWidth: Number(this.imageVariantForm.cropWidth), cropHeight: Number(this.imageVariantForm.cropHeight)
            };
            window.addEventListener("pointermove", this.updateImageEditorInteraction);
            window.addEventListener("pointerup", this.endImageEditorInteraction);
        },
        beginImageStageResize(event, direction) {
            if (this.imageVariantForm.mode !== "stretch") return;
            event.preventDefault();
            event.stopPropagation();
            const stage = this.$refs.imageVariantStage;
            if (!stage) return;
            if (event.currentTarget?.setPointerCapture && Number.isInteger(event.pointerId)) {
                try { event.currentTarget.setPointerCapture(event.pointerId); } catch (_) { /* Browser may already own capture. */ }
            }
            this.imageEditorResizeDirection = direction;
            document.body.classList.add("body--image-resizing");
            document.body.dataset.imageResizeDirection = direction;
            this.imageEditorInteraction = {
                kind: "resize", direction,
                pointerId: event.pointerId,
                startClientX: event.clientX, startClientY: event.clientY,
                stageRect: stage.getBoundingClientRect(),
                width: Number(this.imageVariantForm.width), height: Number(this.imageVariantForm.height),
                boxX: Number(this.imageStretchBox.x), boxY: Number(this.imageStretchBox.y),
                boxWidth: Number(this.imageStretchBox.width), boxHeight: Number(this.imageStretchBox.height)
            };
            window.addEventListener("pointermove", this.updateImageEditorInteraction, { passive: false });
            window.addEventListener("pointerup", this.endImageEditorInteraction);
            window.addEventListener("pointercancel", this.endImageEditorInteraction);
        },
        updateImageEditorInteraction(event) {
            const interaction = this.imageEditorInteraction;
            if (!interaction) return;
            if (interaction.pointerId != null && event.pointerId != null && interaction.pointerId !== event.pointerId) return;
            event.preventDefault();
            if (interaction.kind === "crop") {
                const dx = ((event.clientX - interaction.startClientX) / Math.max(1, interaction.stageRect.width)) * 100;
                const dy = ((event.clientY - interaction.startClientY) / Math.max(1, interaction.stageRect.height)) * 100;
                const minimum = 5;
                let x = interaction.cropX, y = interaction.cropY;
                let width = interaction.cropWidth, height = interaction.cropHeight;
                if (interaction.action === "move") {
                    x = Math.max(0, Math.min(100 - width, interaction.cropX + dx));
                    y = Math.max(0, Math.min(100 - height, interaction.cropY + dy));
                } else {
                    if (interaction.action.includes("e")) width = Math.max(minimum, Math.min(100 - x, interaction.cropWidth + dx));
                    if (interaction.action.includes("s")) height = Math.max(minimum, Math.min(100 - y, interaction.cropHeight + dy));
                    if (interaction.action.includes("w")) {
                        const nextX = Math.max(0, Math.min(interaction.cropX + interaction.cropWidth - minimum, interaction.cropX + dx));
                        width = interaction.cropWidth + interaction.cropX - nextX; x = nextX;
                    }
                    if (interaction.action.includes("n")) {
                        const nextY = Math.max(0, Math.min(interaction.cropY + interaction.cropHeight - minimum, interaction.cropY + dy));
                        height = interaction.cropHeight + interaction.cropY - nextY; y = nextY;
                    }
                }
                this.imageVariantForm.cropX = Math.round(x * 10) / 10;
                this.imageVariantForm.cropY = Math.round(y * 10) / 10;
                this.imageVariantForm.cropWidth = Math.round(width * 10) / 10;
                this.imageVariantForm.cropHeight = Math.round(height * 10) / 10;
                return;
            }
            const stageWidth = Math.max(1, interaction.stageRect.width);
            const stageHeight = Math.max(1, interaction.stageRect.height);
            const dxPercent = ((event.clientX - interaction.startClientX) / stageWidth) * 100;
            const dyPercent = ((event.clientY - interaction.startClientY) / stageHeight) * 100;
            const minimumBoxSize = 12;
            let boxX = interaction.boxX, boxY = interaction.boxY;
            let boxWidth = interaction.boxWidth, boxHeight = interaction.boxHeight;
            if (interaction.direction.includes("e")) {
                boxWidth = Math.max(minimumBoxSize, Math.min(100 - boxX, interaction.boxWidth + dxPercent));
            }
            if (interaction.direction.includes("s")) {
                boxHeight = Math.max(minimumBoxSize, Math.min(100 - boxY, interaction.boxHeight + dyPercent));
            }
            if (interaction.direction.includes("w")) {
                const nextX = Math.max(0, Math.min(interaction.boxX + interaction.boxWidth - minimumBoxSize, interaction.boxX + dxPercent));
                boxWidth = interaction.boxWidth + interaction.boxX - nextX;
                boxX = nextX;
            }
            if (interaction.direction.includes("n")) {
                const nextY = Math.max(0, Math.min(interaction.boxY + interaction.boxHeight - minimumBoxSize, interaction.boxY + dyPercent));
                boxHeight = interaction.boxHeight + interaction.boxY - nextY;
                boxY = nextY;
            }
            this.imageStretchBox = {
                x: Math.round(boxX * 10) / 10,
                y: Math.round(boxY * 10) / 10,
                width: Math.round(boxWidth * 10) / 10,
                height: Math.round(boxHeight * 10) / 10
            };

            const widthScale = boxWidth / Math.max(1, interaction.boxWidth);
            const heightScale = boxHeight / Math.max(1, interaction.boxHeight);
            this.imageVariantForm.width = Math.round(Math.max(256, Math.min(2400, interaction.width * widthScale)));
            this.imageVariantForm.height = Math.round(Math.max(256, Math.min(2400, interaction.height * heightScale)));
        },
        endImageEditorInteraction() {
            this.imageEditorInteraction = null;
            this.imageEditorResizeDirection = "";
            document.body.classList.remove("body--image-resizing");
            delete document.body.dataset.imageResizeDirection;
            window.removeEventListener("pointermove", this.updateImageEditorInteraction);
            window.removeEventListener("pointerup", this.endImageEditorInteraction);
            window.removeEventListener("pointercancel", this.endImageEditorInteraction);
        },        async deriveCoverImage() {
            const target = this.resolveAiCoverTarget();
            if (!target.postId || !this.imageVariantForm.imageUrl) {
                this.showError("Choose an image to edit first.");
                return;
            }
            this.imageVariantLoading = true;
            const data = await this.request(`/api/admin/publications/${target.postId}/ai-cover/derive`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(this.imageVariantForm)
            }, "Edited candidate created. The original image was preserved.", null, true);
            this.imageVariantLoading = false;
            if (data?.imageUrl) {
                const candidate = {
                    candidateId: `derived-${Date.now()}`,
                    imageUrl: data.imageUrl,
                    sourceType: "source-derived",
                    recommended: false,
                    recommendationReason: "Edited local variant of the selected image.",
                    warnings: []
                };
                this.aiCoverCandidates.push(candidate);
                this.imageVariantForm.imageUrl = data.imageUrl;
            }
        },
        openXhsCoverEditor(candidate = null) {
            const selected = candidate || this.aiCoverCandidates[0];
            this.xhsCoverForm.imageUrl = selected?.imageUrl || "";
            this.xhsCoverForm.headline = this.postForm?.title || this.selectedAdminPost?.title || "";
            this.xhsCoverResultUrl = "";
        },
        syncCoverEditorCandidate(candidate) {
            if (!candidate) return;
            this.openXhsCoverEditor(candidate);
            this.imageVariantForm.imageUrl = candidate.imageUrl || "";
        },
        setAiCoverPreviewMode(mode) {
            this.aiCoverPreviewMode = mode;
            const candidate = this.aiCoverCandidates.find((item) => item?.imageUrl === this.xhsCoverForm.imageUrl)
                || this.aiCoverCandidates[0];
            if (candidate) this.syncCoverEditorCandidate(candidate);
        },
        xhsCoverPreviewUrl() {
            // The editor renders the template layers itself. Always use the raw
            // candidate here so an existing flattened template is never used as
            // the background of another template.
            return this.xhsCoverForm.imageUrl || "";
        },
        async createCustomXhsCover() {
            const target = this.resolveAiCoverTarget();
            if (!target.postId || !this.xhsCoverForm.imageUrl) {
                this.showError(this.siteLanguage === "zh" ? "请先选择一张候选图片。" : "Choose a candidate image first.");
                return;
            }
            this.xhsCoverEditorLoading = true;
            const data = await this.request(`/api/admin/publications/${target.postId}/ai-cover/social-cover`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(this.xhsCoverForm)
            }, "", null, true);
            this.xhsCoverEditorLoading = false;
            if (data?.imageUrl) {
                this.xhsCoverResultUrl = data.imageUrl;
                this.showSuccess(this.siteLanguage === "zh" ? "已生成可下载的小红书封面，原图未被覆盖。" : "Editable Xiaohongshu cover created; the original is unchanged.");
            }
        },
        syncSelectedSourceCandidate() {
            const sources = this.sourceAiCoverCandidates;
            const current = sources.find((candidate) => String(candidate?.candidateId || candidate?.imageUrl || "") === String(this.selectedSourceCandidateId || ""));
            this.selectedSourceCandidateId = String((current || sources[0])?.candidateId || (current || sources[0])?.imageUrl || "");
        },
        sourceCandidateOptionLabel(candidate, index) {
            const page = candidate?.pageNumber ? ` · page ${candidate.pageNumber}` : "";
            return `Original image ${index + 1}${page}`;
        },
        candidatePreviewUrl(candidate) {
            if (this.aiCoverPreviewMode === "social" && candidate?.socialCoverUrl) {
                return candidate.socialCoverUrl;
            }
            return candidate?.imageUrl || "";
        },
        candidateSourceLabel(candidate) {
            if (candidate?.sourceType === "qwen") return "Qwen AI image";
            if (candidate?.sourceType === "doubao") return "Doubao Seedream AI image";
            if (candidate?.sourceType === "comfyui") return "ComfyUI / IP-Adapter";
            if (candidate?.sourceType === "source-original") return "Original paper image";
            if (candidate?.sourceType === "method-summary") return "Method summary";
            const details = `${candidate?.candidateId || ""} ${candidate?.recommendationReason || ""} ${candidate?.prompt || ""} ${candidate?.imageUrl || ""}`.toLowerCase();
            if (details.includes("qwen")) {
                return "Qwen AI image";
            }
            if (details.includes("doubao") || details.includes("seedream")) {
                return "Doubao Seedream AI image";
            }
            if (details.includes("comfy") || details.includes("ip-adapter") || details.includes("local-comfyui")) {
                return "ComfyUI / IP-Adapter";
            }
            if (details.includes("source provenance") || details.includes("paper figure") || details.includes("source-extracted") || details.includes("cached-paper-images")) {
                return "Original paper image";
            }
            return candidate?.seed ? "AI-generated image" : "Paper image";
        },
        aiCoverStatusLabel(result) {
            if (!result) {
                return "";
            }
            if (result.status === "FAILED") {
                const failure = Array.isArray(result.warnings)
                    ? result.warnings.find((warning) => String(warning || "").includes("DOUBAO_IMAGE_GENERATION_FAILED"))
                    : "";
                return failure || (this.siteLanguage === "zh" ? "候选图生成失败，请检查相关服务后重试。" : "Image generation failed. Check the configured services and retry.");
            }
            const understandingSource = String(
                result.paperUnderstanding?.understandingSource
                || result.applicationSceneBrief?.sourceProvider
                || ""
            );
            if (this.siteLanguage === "en") {
                return understandingSource.includes("EVIDENCE_FALLBACK")
                    ? "Candidates were generated from GROBID, DOI, and PDF evidence. Review them before choosing a cover."
                    : "Paper-grounded image candidates are ready. Review them before choosing a cover.";
            }
            return understandingSource.includes("EVIDENCE_FALLBACK")
                ? "已基于 GROBID/DOI/PDF 证据生成候选图，请人工检查后再选择封面。"
                : "已生成论文相关候选图，请人工检查后再选择封面。";
        },
        async generateAiCoverCandidates(options = {}) {
            const notify = options?.notify !== false;
            let target = this.resolveAiCoverTarget();

            // Uploading a PDF stores the file first; persist the URL before the
            // server-side generator resolves the publication from the database.
            if (options?.persistPublication !== false && this.currentView === "publish" && this.postForm?.pdfUrl) {
                const savedPost = await this.submitPost();
                if (!savedPost) {
                    return null;
                }
                target = this.resolveAiCoverTarget();
            }
            if (!target.postId || !target.post) {
                this.showError(this.siteLanguage === "zh" ? "请先保存并打开一篇论文。" : "Save and open a publication before generating images.");
                return null;
            }
            if (!target.post.pdfUrl) {
                this.showError(this.siteLanguage === "zh" ? "这篇论文还没有 PDF 文件。" : "No PDF file is available for this publication.");
                return null;
            }
            this.aiCoverGenerationLoading = true;
            this.startAiCoverProgress();
            this.aiCoverGenerationResult = null;
            const job = await this.request(`/api/admin/publications/${target.postId}/ai-cover/generate`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(this.imageGenerationOptions)
            }, "", null, true);
            let data = job;
            if (job?.status === "RUNNING" && job.jobId) {
                data = await this.waitForAiCoverGeneration(job.jobId);
            }
            this.finishAiCoverProgress(Boolean(data && data.status !== "FAILED"));
            this.aiCoverGenerationLoading = false;
            this.aiCoverCandidateFilter = "all";
            if (!data) {
                return null;
            }
            this.aiCoverGenerationResult = data;
            this.aiCoverCandidates = Array.isArray(data.candidates) ? data.candidates : [];
            this.syncSelectedSourceCandidate();
            this.imageVariantForm.imageUrl = this.selectedSourceAiCoverCandidate?.imageUrl || this.aiCoverCandidates[0]?.imageUrl || "";
            this.openXhsCoverEditor(this.aiCoverCandidates[0]);
            if (data.status === "FAILED") {
                if (notify) {
                    this.showError(this.aiCoverStatusLabel(data) || data.message || "Reliable AI representative image generation failed. The publication itself was not affected. Please check the image services and retry.");
                }
                return data;
            }
            if (notify) {
                this.showSuccess(this.aiCoverStatusLabel(data));
            }
            return data;
        },
        startAiCoverProgress() {
            if (this.aiCoverProgressTimer) {
                window.clearInterval(this.aiCoverProgressTimer);
            }
            this.aiCoverGenerationStartedAt = Date.now();
            this.aiCoverGenerationProgress = 3;
            this.aiCoverGenerationStage = this.siteLanguage === "zh"
                ? "正在读取论文并检查已有分析缓存..."
                : "Reading the paper and checking cached analysis...";
            this.aiCoverProgressTimer = window.setInterval(() => {
                const seconds = Math.max(0, (Date.now() - this.aiCoverGenerationStartedAt) / 1000);
                if (seconds < 12) {
                    this.aiCoverGenerationProgress = Math.min(18, 3 + Math.round(seconds * 1.25));
                    this.aiCoverGenerationStage = this.siteLanguage === "zh" ? "正在读取论文并提取原图..." : "Reading the PDF and extracting source images...";
                } else if (seconds < 35) {
                    this.aiCoverGenerationProgress = Math.min(38, 18 + Math.round((seconds - 12) * 0.87));
                    this.aiCoverGenerationStage = this.siteLanguage === "zh" ? "正在分析论文并准备生图提示词..." : "Analysing the paper and preparing image prompts...";
                } else if (seconds < 120) {
                    this.aiCoverGenerationProgress = Math.min(82, 38 + Math.round((seconds - 35) * 0.52));
                    this.aiCoverGenerationStage = this.siteLanguage === "zh" ? "云端模型与 ComfyUI 正在并行生成图片..." : "The cloud model and ComfyUI are generating in parallel...";
                } else {
                    this.aiCoverGenerationProgress = Math.min(94, 82 + Math.round((seconds - 120) / 15));
                    this.aiCoverGenerationStage = this.siteLanguage === "zh" ? "正在完成质量检查并保存候选图片..." : "Completing quality checks and saving candidates...";
                }
            }, 1000);
        },
        finishAiCoverProgress(success) {
            if (this.aiCoverProgressTimer) {
                window.clearInterval(this.aiCoverProgressTimer);
                this.aiCoverProgressTimer = null;
            }
            if (success) {
                this.aiCoverGenerationProgress = 100;
                this.aiCoverGenerationStage = this.siteLanguage === "zh" ? "候选图片已生成。" : "Image candidates are ready.";
            } else {
                this.aiCoverGenerationStage = this.siteLanguage === "zh" ? "生成未完成，请查看错误提示后重试。" : "Generation did not complete. Review the error and retry.";
            }
        },
        async waitForAiCoverGeneration(jobId) {
            const maxPolls = 360;
            for (let poll = 0; poll < maxPolls; poll += 1) {
                await new Promise(resolve => window.setTimeout(resolve, 2000));
                const job = await this.request(
                    `/api/admin/publications/${this.resolveAiCoverTarget().postId}/ai-cover/generate/status/${encodeURIComponent(jobId)}`,
                    {},
                    "",
                    null,
                    true
                );
                if (!job) {
                    return null;
                }
                if (job.status === "COMPLETED" || job.status === "FAILED") {
                    return job.result || {
                        status: job.status,
                        message: job.message,
                        candidates: []
                    };
                }
            }
            this.showError(this.siteLanguage === "zh"
                ? "图片仍在后台生成中，请稍后刷新候选图片。"
                : "Images are still generating in the background. Refresh the candidates shortly.");
            return null;
        },
        async generateAiContentBundle() {
            if (!this.currentUser) {
                this.showError("Save a publication before generating copy and images.");
                return;
            }

            let target = this.resolveAiCoverTarget();
            if (this.currentView === "publish" && this.postForm?.pdfUrl) {
                const savedPost = await this.submitPost();
                if (!savedPost) {
                    return;
                }
                target = this.resolveAiCoverTarget();
            }
            if (!target.postId || !target.post) {
                this.showError(this.siteLanguage === "zh" ? "请先上传 PDF。" : "Upload a PDF before generating copy and images.");
                return;
            }
            if (!target.post.pdfUrl) {
                this.showError(this.siteLanguage === "zh" ? "请先上传 PDF，再生成文案和图片。" : "Upload a PDF before generating copy and images.");
                return;
            }
            this.aiContentGenerationLoading = true;
            let copyReady = false;
            let imagesReady = false;
            try {
                this.aiContentGenerationStage = this.siteLanguage === "zh" ? "正在生成基于论文证据的文案..." : "Generating evidence-grounded social copy...";
                copyReady = Boolean(await this.generateSocialCopyForPost(target.post, false));
                this.aiContentGenerationStage = this.siteLanguage === "zh" ? "正在生成论文候选图片..." : "Generating representative image candidates...";
                const imageResult = await this.generateAiCoverCandidates({ notify: false, persistPublication: false });
                imagesReady = Boolean(imageResult && imageResult.status !== "FAILED");
            } finally {
                this.aiContentGenerationLoading = false;
                this.aiContentGenerationStage = "";
            }
            if (copyReady && imagesReady) {
                this.showSuccess(this.siteLanguage === "zh" ? "文案和候选图片已生成，可以开始检查。" : "Social copy and image candidates are ready for review.");
            } else if (copyReady || imagesReady) {
                this.showError(this.siteLanguage === "zh" ? "其中一项已生成，另一项生成失败，可以在下方单独重试。" : "One result is ready, but the other generator needs attention. You can retry it separately below.");
            }
        },
        async loadAiCoverCandidates(postId = this.resolveAiCoverTarget().postId) {
            this.aiCoverGenerationResult = null;
            this.aiCoverCandidates = [];
            this.aiCoverCandidateFilter = "all";
            if (!postId) {
                return;
            }
            const data = await this.request(`/api/admin/publications/${postId}/ai-cover/candidates`, {
                method: "GET"
            }, "", null, true);
            this.aiCoverCandidates = Array.isArray(data) ? data : [];
            this.syncSelectedSourceCandidate();
            this.imageVariantForm.imageUrl = this.selectedSourceAiCoverCandidate?.imageUrl || this.aiCoverCandidates[0]?.imageUrl || "";
        },
        async clearAiCoverCandidates() {
            const target = this.resolveAiCoverTarget();
            if (!target.postId) {
                return;
            }
            const data = await this.request(`/api/admin/publications/${target.postId}/ai-cover/candidates`, {
                method: "DELETE"
            }, "", null, true);
            if (!data) {
                return;
            }
            this.aiCoverCandidateFilter = "all";
            this.aiCoverCandidates = [];
            this.selectedSourceCandidateId = "";
            this.imageVariantForm.imageUrl = "";
            this.aiCoverGenerationResult = null;
            this.showSuccess("Stale AI candidates were cleared; the publication, PDF, and selected cover were preserved.");
        },
        async selectAiCoverCandidate(candidate) {
            const target = this.resolveAiCoverTarget();
            if (!target.postId || !candidate?.imageUrl) {
                this.showError("Selected candidate image does not exist.");
                return;
            }
            this.aiCoverSelectionLoading = true;
            const data = await this.request(`/api/admin/publications/${target.postId}/ai-cover/select`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    candidateId: candidate.candidateId,
                    imageUrl: candidate.imageUrl
                })
            }, "", null, true);
            this.aiCoverSelectionLoading = false;
            if (!data) {
                return;
            }
            if (this.selectedAdminPost && String(this.selectedAdminPost.id) === String(target.postId)) {
                this.selectedAdminPost.coverImageUrl = data.coverImageUrl;
            }
            if (this.selectedPost && String(this.selectedPost.id) === String(target.postId)) {
                this.selectedPost.coverImageUrl = data.coverImageUrl;
            }
            if (String(this.adminPreviewPostId) === String(target.postId) && this.adminPreviewPost) {
                this.adminPreviewPost.coverImageUrl = data.coverImageUrl;
            }
            if (this.currentView === "publish" && String(this.editingPostId) === String(target.postId)) {
                this.postForm.coverImageUrl = data.coverImageUrl;
                this.coverFileLabel = data.coverImageUrl.split("/").pop();
            }
            this.showSuccess(data.message || "Selected image has been saved as the publication cover.");
            await this.refreshAdminViews();
            this.hasDefaultHomepagePostsCache = false;
            await this.ensureDefaultHomepagePostsCache();
            await this.fetchPosts();
        },
        isSelectedAiCoverCandidate(candidate) {
            const target = this.resolveAiCoverTarget();
            return Boolean(candidate?.imageUrl && target.post?.coverImageUrl === candidate.imageUrl);
        },
        isRankedAiCoverRecommendation(candidate) {
            return Boolean(candidate?.recommended && candidate?.sceneScore);
        },
        async uploadImage(file) {
            const formData = new FormData();
            formData.append("file", file);
            try {
                const response = await fetch("/api/uploads/images", {
                    method: "POST",
                    headers: this.authHeaders(),
                    body: formData
                });
                const payload = await this.readJsonSafely(response);
                if (!payload.success) {
                    throw new Error(this.resolveUploadErrorMessage(payload?.message, "Image upload failed."));
                }
                this.showSuccess(`Image uploaded: ${payload.data.fileName}`);
                return payload.data;
            } catch (error) {
                this.showError(this.resolveUploadErrorMessage(error?.message, "Image upload failed."));
                return null;
            }
        },
        async uploadPdf(file) {
            const formData = new FormData();
            formData.append("file", file);
            this.pdfUploadProgress = 0;
            return new Promise((resolve) => {
                const xhr = new XMLHttpRequest();
                xhr.open("POST", "/api/uploads/pdfs", true);
                const headers = this.authHeaders();
                Object.entries(headers || {}).forEach(([name, value]) => xhr.setRequestHeader(name, value));
                xhr.upload.onprogress = (event) => {
                    if (event.lengthComputable && event.total > 0) {
                        this.pdfUploadProgress = Math.min(99, Math.round((event.loaded / event.total) * 100));
                    }
                };
                xhr.onload = () => {
                    let payload = null;
                    try {
                        payload = JSON.parse(xhr.responseText || "{}");
                    } catch (_) {
                        payload = { success: false, message: "" };
                    }
                    if (xhr.status >= 200 && xhr.status < 300 && payload?.success) {
                        this.pdfUploadProgress = 100;
                        this.showSuccess(`PDF uploaded: ${payload.data.fileName}`);
                        resolve(payload.data);
                        return;
                    }
                    this.showError(this.resolveUploadErrorMessage(payload?.message, "PDF upload failed."));
                    resolve(null);
                };
                xhr.onerror = () => {
                    this.showError(this.siteLanguage === "zh" ? "PDF 上传网络中断，请检查连接后重试。" : "The PDF upload was interrupted. Check the connection and retry.");
                    resolve(null);
                };
                xhr.ontimeout = () => {
                    this.showError(this.siteLanguage === "zh" ? "PDF 上传超时，请重试。" : "The PDF upload timed out. Please retry.");
                    resolve(null);
                };
                xhr.timeout = 10 * 60 * 1000;
                xhr.send(formData);
            });
        },
        async uploadProfileAvatar(file) {
            const formData = new FormData();
            formData.append("file", file);
            try {
                const response = await fetch("/api/uploads/profile-avatar", {
                    method: "POST",
                    headers: this.authHeaders(),
                    body: formData
                });
                const payload = await this.readJsonSafely(response);
                if (!payload.success) {
                    throw new Error(this.resolveUploadErrorMessage(payload?.message, "Avatar upload failed."));
                }
                this.showSuccess(`Avatar uploaded: ${payload.data.fileName}`);
                return payload.data;
            } catch (error) {
                this.showError(this.resolveUploadErrorMessage(error?.message, "Avatar upload failed."));
                return null;
            }
        },
        async approveSelectedAdminPost() {
            if (!this.selectedAdminPostId) {
                return;
            }
            const reviewedPostId = this.selectedAdminPostId;
            const data = await this.request(`/api/admin/posts/${this.selectedAdminPostId}/review`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ action: "APPROVE", reason: "" })
            }, "Publication approved successfully.", null, true);
            if (data) {
                this.adminReviewReason = "";
                await this.refreshAdminViews();
                if (this.pendingQueueReturnToApprovals && data.status !== "PENDING_REVIEW") {
                    this.selectedAdminPostId = reviewedPostId;
                    this.selectedAdminPost = data;
                    this.pendingQueueReturnToApprovals = false;
                    this.adminSection = "approvals";
                } else {
                    await this.openAdminPost(this.selectedAdminPostId);
                }
                await this.fetchPosts();
            }
        },
        async rejectSelectedAdminPost() {
            if (!this.selectedAdminPostId) {
                return;
            }
            const reviewedPostId = this.selectedAdminPostId;
            if (!this.adminReviewReason.trim()) {
                this.showError("Please provide a rejection reason before rejecting this publication.");
                return;
            }
            const data = await this.request(`/api/admin/posts/${this.selectedAdminPostId}/review`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ action: "REJECT", reason: this.adminReviewReason })
            }, "Publication rejected successfully.", null, true);
            if (data) {
                await this.refreshAdminViews();
                if (this.pendingQueueReturnToApprovals && data.status !== "PENDING_REVIEW") {
                    this.selectedAdminPostId = reviewedPostId;
                    this.selectedAdminPost = data;
                    this.pendingQueueReturnToApprovals = false;
                    this.adminSection = "approvals";
                } else {
                    await this.openAdminPost(this.selectedAdminPostId);
                }
            }
        },
        async quickApproveFromQueue(postId) {
            const data = await this.request(`/api/admin/posts/${postId}/review`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ action: "APPROVE", reason: "" })
            }, "Publication approved successfully.", null, true);
            if (data) {
                if (this.selectedAdminPostId === postId) {
                    this.selectedAdminPost = data;
                }
                await this.refreshAdminViews();
                await this.fetchPosts();
            }
        },
        async openReviewFromQueue(postId) {
            this.pendingQueueReturnToApprovals = true;
            await this.selectAdminSection("articles");
            await this.openAdminPost(postId);
        },
        async returnToPendingQueue() {
            this.adminSection = "approvals";
            this.pendingQueueReturnToApprovals = false;
            await this.fetchPendingQueue();
        },
        clearPendingQueueSearch() {
            this.pendingQueueQuery = "";
        },
        async updateAdminUserRole(userId, role) {
            const data = await this.request(`/api/admin/users/${userId}/role`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ role })
            }, "User role updated successfully.", null, true);
            if (data) {
                await this.fetchAdminUsers();
            }
        },
        async activateAdminUser(userId) {
            const data = await this.request(`/api/admin/users/${userId}/activate`, {
                method: "POST"
            }, "User account activated successfully.", null, true);
            if (data) {
                await this.fetchAdminUsers();
            }
        },
        async deactivateAdminUser(userId) {
            const data = await this.request(`/api/admin/users/${userId}/deactivate`, {
                method: "POST"
            }, "User account deactivated successfully.", null, true);
            if (data) {
                await this.fetchAdminUsers();
            }
        },
        async approveContributorApplication(applicationId) {
            const data = await this.request(`/api/admin/contributor-applications/${applicationId}/approve`, {
                method: "POST"
            }, "Contributor application approved successfully.", null, true);
            if (data) {
                this.contributorApplicationRejectReasons[applicationId] = "";
                await this.fetchAdminContributorApplications();
                await this.fetchAdminUsers();
            }
        },
        async rejectContributorApplication(applicationId) {
            const reason = (this.contributorApplicationRejectReasons[applicationId] || "").trim();
            if (!reason) {
                this.showError("Please provide a rejection reason before rejecting this application.");
                return;
            }
            const data = await this.request(`/api/admin/contributor-applications/${applicationId}/reject`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ reason })
            }, "Contributor application rejected successfully.", null, true);
            if (data) {
                this.contributorApplicationRejectReasons[applicationId] = "";
                await this.fetchAdminContributorApplications();
                await this.fetchAdminUsers();
            }
        },
        removePostFromPublicCaches(postId) {
            const targetId = String(postId);
            this.posts = this.posts.filter((post) => String(post.id) !== targetId);
            this.defaultHomepagePostsCache = this.defaultHomepagePostsCache.filter((post) => String(post.id) !== targetId);
            if (String(this.selectedPostId) === targetId) {
                this.selectedPostId = null;
                this.selectedPost = null;
            }
            this.ensurePostPageInRange();
        },
        async updateAdminPostArchiveState(postId, action, successMessage) {
            if (!postId) {
                return null;
            }
            const data = await this.request(`/api/admin/posts/${postId}/${action}`, {
                method: "POST"
            }, successMessage, null, true);
            if (!data) {
                return null;
            }
            if (this.selectedAdminPostId === postId) {
                this.selectedAdminPost = data;
                this.adminReviewReason = data.rejectReason || "";
            }
            if (this.adminPreviewPostId === postId) {
                this.adminPreviewPost = data;
            }
            if (data.status === "ARCHIVED") {
                this.removePostFromPublicCaches(postId);
            }
            await this.refreshAdminViews();
            this.hasDefaultHomepagePostsCache = false;
            await this.ensureDefaultHomepagePostsCache();
            await this.fetchPosts();
            if (this.currentUser) {
                await this.fetchMyPosts();
            }
            return data;
        },
        async archiveSelectedAdminPost() {
            if (!this.selectedAdminPostId) {
                return;
            }
            await this.updateAdminPostArchiveState(this.selectedAdminPostId, "archive", "Publication archived successfully.");
        },
        async restoreSelectedAdminPost() {
            if (!this.selectedAdminPostId) {
                return;
            }
            await this.updateAdminPostArchiveState(this.selectedAdminPostId, "restore", "Publication restored successfully.");
        },
        async archivePreviewAdminPost() {
            if (!this.adminPreviewPostId) {
                return;
            }
            await this.updateAdminPostArchiveState(this.adminPreviewPostId, "archive", "Publication archived successfully.");
        },
        async restorePreviewAdminPost() {
            if (!this.adminPreviewPostId) {
                return;
            }
            await this.updateAdminPostArchiveState(this.adminPreviewPostId, "restore", "Publication restored successfully.");
        },
        async request(url, options = {}, successMessage = "", onSuccess, requiresAuth = false) {
            this.errorMessage = "";
            try {
                const headers = requiresAuth ? this.authHeaders(options.headers || {}) : (options.headers || {});
                const response = await fetch(url, {
                    ...options,
                    headers
                });
                const payload = await response.json();
                if (!payload.success) {
                    throw new Error(this.translateBackendMessage(payload.message) || "The request could not be completed.");
                }
                if (successMessage) {
                    this.showSuccess(successMessage);
                }
                if (onSuccess) {
                    await onSuccess(payload.data);
                }
                return payload.data;
            } catch (error) {
                this.showError(error.message || "The request could not be completed.");
                return null;
            }
        },
        buildHeatScore(post) {
            const views = Number(post.viewCount || 0);
            const comments = Number(post.commentCount || 0);
            return Math.round((views * 0.8) + (comments * 0.2));
        },
        statusLabel(status) {
            const mapping = {
                PUBLISHED: "Published",
                PENDING: "Pending",
                PENDING_REVIEW: "Pending Review",
                APPROVED: "Approved",
                DRAFT: "Draft",
                REJECTED: "Rejected",
                ARCHIVED: "Archived"
            };
            return mapping[status] || status || "Pending";
        },
        adminSortLabel(sort) {
            const mapping = {
                UPDATED_DESC: "Recently updated",
                SUBMITTED_DESC: "Recently submitted",
                REVIEWED_DESC: "Recently reviewed"
            };
            return mapping[sort] || "Recently updated";
        },
        roleLabel(role) {
            const mapping = {
                USER: "User",
                CONTRIBUTOR: "Contributor",
                ADMIN: "Admin"
            };
            return mapping[role] || role || "User";
        },
        statusClass(status) {
            const label = this.statusLabel(status).toLowerCase().replace(/\s+/g, "-");
            return `status-chip--${label}`;
        },
        adminSortValue(row) {
            if (this.adminSortOption === "SUBMITTED_DESC") {
                return row.submittedAt || row.updatedAt;
            }
            if (this.adminSortOption === "REVIEWED_DESC") {
                return row.reviewedAt || row.updatedAt;
            }
            return row.updatedAt;
        },
        reviewerLabel(post) {
            return post?.reviewerName || post?.reviewedBy || "";
        },
        canManageUserRole(user) {
            return user?.role === "USER" || user?.role === "CONTRIBUTOR";
        },
        nextUserRole(user) {
            return user?.role === "USER" ? "CONTRIBUTOR" : "USER";
        },
        nextUserRoleLabel(user) {
            return user?.role === "USER" ? "Promote to Contributor" : "Change to User";
        },
        nextUserActiveActionLabel(user) {
            return user?.active ? "Deactivate" : "Activate";
        },
        contributorApplicationTitle() {
            return "Contributor Access Application";
        },
        contributorApplicationNote(application) {
            if (application?.status === "PENDING") {
                return "Your contributor application is waiting for administrator review.";
            }
            if (application?.status === "APPROVED") {
                return "Your contributor application was approved.";
            }
            if (application?.status === "REJECTED") {
                return application?.rejectReason
                    ? `Your contributor application was rejected: ${application.rejectReason}`
                    : "Your contributor application was rejected.";
            }
            return "";
        },
        scrollToTopForMessage() {
            window.requestAnimationFrame(() => {
                window.scrollTo({ top: 0, behavior: "smooth" });
            });
        },
        showError(message) {
            this.errorMessage = message;
            this.successMessage = "";
        },
        showSuccess(message) {
            this.successMessage = message;
            this.errorMessage = "";
            window.setTimeout(() => {
                if (this.successMessage === message) {
                    this.successMessage = "";
                }
            }, 2600);
        },
        formatDate(value) {
            if (!value) {
                return "";
            }
            return new Date(value).toLocaleString("en-GB", {
                year: "numeric",
                month: "short",
                day: "2-digit",
                hour: "2-digit",
                minute: "2-digit"
            });
        },
        formatShortDate(value) {
            if (!value) {
                return "";
            }
            return new Date(value).toLocaleDateString("en-GB", {
                year: "numeric",
                month: "short",
                day: "2-digit"
            });
        },
        hasPaperMetadata(post) {
            return Boolean(
                post?.publication ||
                post?.publicationAuthors ||
                post?.publicationYear ||
                post?.venue ||
                post?.abstractText ||
                post?.keywords ||
                post?.doi ||
                post?.pdfUrl ||
                post?.bibtex ||
                post?.researchArea ||
                post?.codeUrl ||
                post?.datasetUrl
            );
        },
        paperYearLabel(post) {
            if (post?.publicationYear) {
                return post.publicationYear;
            }
            if (!post?.createdAt) {
                return "";
            }
            return new Date(post.createdAt).getFullYear();
        },
        paperAuthorsLabel(post) {
            return post?.publicationAuthors || post?.authorName || "";
        },
        paperKeywordList(post) {
            return String(post?.keywords || "")
                .split(/[,;|]/)
                .map((keyword) => keyword.trim())
                .filter(Boolean)
                .slice(0, 6);
        },
        abstractPreview(post, limit = 180) {
            const text = String(post?.abstractText || "").replace(/\s+/g, " ").trim();
            if (!text) {
                return "";
            }
            return text.length > limit ? `${text.slice(0, limit).trim()}...` : text;
        },
        doiHref(doi) {
            const value = String(doi || "").trim();
            if (!value) {
                return "";
            }
            if (/^https?:\/\//i.test(value)) {
                return value;
            }
            return `https://doi.org/${value.replace(/^doi:\s*/i, "")}`;
        },
        displayBibtex(post) {
            if (post?.bibtex) {
                return post.bibtex;
            }
            if (!this.hasPaperMetadata(post)) {
                return "";
            }
            const year = this.paperYearLabel(post) || "n.d.";
            const authors = this.paperAuthorsLabel(post) || "Unknown Author";
            const keySeed = `${authors.split(/[,;]/)[0] || "paper"}${year}${post?.title || ""}`;
            const key = keySeed
                .toLowerCase()
                .replace(/[^a-z0-9]+/g, "-")
                .replace(/^-|-$/g, "")
                .slice(0, 48) || "paper";
            const lines = [
                `@article{${key},`,
                `  title = {${post?.title || "Untitled paper"}},`,
                `  author = {${authors}},`,
                `  year = {${year}}`
            ];
            if (post?.venue) {
                lines.push(`  journal = {${post.venue}}`);
            }
            if (post?.doi) {
                lines.push(`  doi = {${post.doi}}`);
            }
            return `${lines.join(",\n")}\n}`;
        },
        async copyBibtex(post) {
            const bibtex = this.displayBibtex(post);
            if (!bibtex) {
                this.showError("No BibTeX is available for this paper yet.");
                return;
            }
            try {
                await navigator.clipboard.writeText(bibtex);
                this.showSuccess("BibTeX copied.");
            } catch (error) {
                this.showError("Unable to copy BibTeX in this browser.");
            }
        },
        hasRealCover(url) {
            return Boolean(resolveImageCandidate(url));
        },
        hasPostProvidedCover(post) {
            return Boolean(resolvePostOwnCoverImage(post));
        },
        getPostOwnCoverImage(post) {
            return resolvePostOwnCoverImage(post);
        },
        getPostCoverImage(post) {
            return resolvePostCoverImage(post);
        },
        getDefaultCoverImage(categoryName) {
            return resolveDefaultCoverImage(categoryName);
        },
        coverCategoryLabel(categoryName) {
            return "Publication";
        },
        coverSurfaceStyle(postOrCoverImageUrl, categoryName) {
            if (postOrCoverImageUrl && typeof postOrCoverImageUrl === "object") {
                return {};
            }
            const coverImage = this.hasRealCover(postOrCoverImageUrl)
                ? postOrCoverImageUrl
                : this.getDefaultCoverImage(categoryName);
            return { backgroundImage: this.buildCover(coverImage) };
        },
        buildCover(url) {
            const asset = this.resolveAsset(url);
            return asset ? `linear-gradient(rgba(43, 28, 19, 0.18), rgba(43, 28, 19, 0.18)), url('${asset}')` : "none";
        },
        resolveAsset(url) {
            return url || "";
        },
        userInitials(value) {
            if (!value) {
                return "HE";
            }
            const parts = String(value).trim().split(/\s+/).slice(0, 2);
            if (parts.length > 1) {
                return parts.map((part) => part.charAt(0).toUpperCase()).join("");
            }
            return value.slice(0, 2).toUpperCase();
        },
        translateCategory(value) {
            if (!value) {
                return "Uncatalogued";
            }
            return categoryMap[value] || value;
        },
        translateNickname(value) {
            if (!value) {
                return "Anonymous Contributor";
            }
            return nicknameMap[value] || value;
        },
        translateText(value) {
            if (!value) {
                return "";
            }
            return textMap[value] || value;
        },
        translateHeritageName(value) {
            if (!value) {
                return "Unnamed publication";
            }
            return heritageMap[value] || value;
        },
        translateRegion(value) {
            if (!value) {
                return "Region not specified";
            }
            return regionMap[value] || value;
        },
        translateBackendMessage(message) {
            if (!message) {
                return "";
            }
            return backendMessageMap[message] || message;
        },
        async readJsonSafely(response) {
            try {
                return await response.json();
            } catch (error) {
                return {
                    success: false,
                    message: response.status === 413 || response.status === 400
                        ? "Image exceeds the 10MB size limit."
                        : ""
                };
            }
        },
        resolveUploadErrorMessage(message, fallbackMessage) {
            const translated = this.translateBackendMessage(message);
            if (translated) {
                return translated;
            }
            if (typeof message === "string" && message.toLowerCase().includes("10mb")) {
                return "Image exceeds the 10MB size limit.";
            }
            return fallbackMessage;
        },
        normaliseOptionalField(value) {
            const trimmed = String(value || "").trim();
            return trimmed ? trimmed : null;
        },
        countBytes(value) {
            return new TextEncoder().encode(String(value || "")).length;
        },
        truncateToByteLimit(value, byteLimit) {
            const encoder = new TextEncoder();
            let totalBytes = 0;
            let result = "";

            for (const character of String(value || "")) {
                const characterBytes = encoder.encode(character).length;
                if (totalBytes + characterBytes > byteLimit) {
                    break;
                }
                result += character;
                totalBytes += characterBytes;
            }

            return result;
        },
        limitPostField(fieldName, byteLimit, event) {
            const rawValue = event?.target?.value ?? this.postForm[fieldName];
            const limitedValue = this.truncateToByteLimit(rawValue, byteLimit);
            this.postForm[fieldName] = limitedValue;

            if (event?.target && event.target.value !== limitedValue) {
                event.target.value = limitedValue;
            }
        },
        validatePostForm() {
            const participantId = String(this.postForm.participantId || "").trim();
            const title = String(this.postForm.title || "").trim();

            if (!participantId) {
                return this.siteLanguage === "zh" ? "请输入 Participant ID。" : "Enter your Participant ID.";
            }
            if (!/^[A-Za-z0-9_-]{2,40}$/.test(participantId)) {
                return this.siteLanguage === "zh"
                    ? "Participant ID 需为 2-40 位，只能包含字母、数字、下划线或连字符。"
                    : "Participant ID must be 2-40 characters using letters, numbers, underscores, or hyphens.";
            }
            if (!title) {
                return this.siteLanguage === "zh" ? "请输入论文标题。" : "Enter the paper title.";
            }
            if (this.countBytes(title) > postTitleByteLimit) {
                return `Title cannot exceed ${postTitleByteLimit} UTF-8 bytes.`;
            }
            if (this.postForm.publicationYear) {
                const year = Number(this.postForm.publicationYear);
                if (!Number.isInteger(year) || year < 1000 || year > 3000) {
                    return "Publication year must be a valid year.";
                }
            }
            if (!this.postForm.pdfUrl) {
                return this.siteLanguage === "zh" ? "请上传论文 PDF。" : "Upload the paper PDF.";
            }
            return "";
        },
        validatePublicationReadyForReview() {
            if (!this.postForm.pdfUrl) {
                return "Please upload the publication PDF before submitting this publication.";
            }
            return "";
        },
        validateRegisterForm() {
            const username = this.registerForm.username.trim();
            const nickname = this.registerForm.nickname.trim();
            const email = String(this.registerForm.email || "").trim();
            const phone = String(this.registerForm.phone || "").trim();
            const password = this.registerForm.password || "";
            const confirmPassword = this.registerForm.confirmPassword || "";

            if (!username) {
                return "Username cannot be empty.";
            }
            if (username.length < 4 || username.length > 50) {
                return "Username must be between 4 and 50 characters.";
            }
            if (!nickname) {
                return "Display name cannot be empty.";
            }
            if (nickname.length > 50) {
                return "Display name cannot exceed 50 characters.";
            }
            if (!password) {
                return "Password cannot be empty.";
            }
            if (password.length < 6) {
                return "Password must be at least 6 characters.";
            }
            if (password !== confirmPassword) {
                return "Passwords do not match.";
            }
            if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                return "Please enter a valid email address.";
            }
            if (phone && phone.length > 20) {
                return "Phone number cannot exceed 20 characters.";
            }
            const securityQuestions = Array.isArray(this.registerForm.securityQuestions)
                ? this.registerForm.securityQuestions
                : [];
            if (securityQuestions.length !== requiredSecurityQuestionCount) {
                return "Exactly 3 security questions are required.";
            }
            for (let i = 0; i < securityQuestions.length; i += 1) {
                const question = securityQuestions[i] || {};
                const questionText = String(question.questionText || "").trim();
                const answer = String(question.answer || "");
                if (!questionText) {
                    return `Security question ${i + 1} cannot be empty.`;
                }
                if (!answer.trim()) {
                    return `Security answer ${i + 1} cannot be empty.`;
                }
            }
            return "";
        },
        validateLoginForm() {
            const username = this.loginForm.username.trim();
            const password = this.loginForm.password || "";

            if (!username || !password) {
                return "Please enter your username and password.";
            }
            return "";
        },
        validatePasswordRecoveryForm() {
            if (!this.hasPasswordRecoveryQuestions) {
                return "Please load your security questions first.";
            }
            const newPassword = this.passwordRecoveryNewPassword || "";
            const confirmPassword = this.passwordRecoveryConfirmPassword || "";
            if (newPassword.length < 6) {
                return "Password must be at least 6 characters.";
            }
            if (newPassword !== confirmPassword) {
                return "Passwords do not match.";
            }
            if (!Array.isArray(this.passwordRecoveryAnswers)
                || this.passwordRecoveryAnswers.length !== requiredSecurityQuestionCount) {
                return "Exactly 3 security answers are required.";
            }
            for (let i = 0; i < this.passwordRecoveryAnswers.length; i += 1) {
                const answer = String(this.passwordRecoveryAnswers[i] || "");
                if (!answer.trim()) {
                    return `Security answer ${i + 1} cannot be empty.`;
                }
            }
            return "";
        },
        validateProfilePasswordChangeForm() {
            if (!this.hasPasswordChangeQuestions) {
                return "Please load your security questions first.";
            }
            const newPassword = this.passwordChangeNewPassword || "";
            const confirmPassword = this.passwordChangeConfirmPassword || "";
            if (newPassword.length < 6) {
                return "Password must be at least 6 characters.";
            }
            if (newPassword !== confirmPassword) {
                return "Passwords do not match.";
            }
            if (!Array.isArray(this.passwordChangeAnswers)
                || this.passwordChangeAnswers.length !== requiredSecurityQuestionCount) {
                return "Exactly 3 security answers are required.";
            }
            for (let i = 0; i < this.passwordChangeAnswers.length; i += 1) {
                const answer = String(this.passwordChangeAnswers[i] || "");
                if (!answer.trim()) {
                    return `Security answer ${i + 1} cannot be empty.`;
                }
            }
            return "";
        },
        storeUser(user) {
            localStorage.setItem("heritage-current-user", JSON.stringify(user));
        },
        renderCharts() {
            this.renderPostLineChart();
            this.renderCommentLineChart();
            this.renderCategoryPieChart();
        },
        renderPostLineChart() {
            const chartDom = document.getElementById("chart-posts");
            if (!chartDom || typeof echarts === "undefined") {
                return;
            }
            const myChart = echarts.getInstanceByDom(chartDom) || echarts.init(chartDom);
            const dateCounts = {};
            this.publishedHomepagePosts.forEach((post) => {
                const date = String(post.createdAt || "").substring(0, 10);
                if (date) {
                    dateCounts[date] = (dateCounts[date] || 0) + 1;
                }
            });
            const sortedDates = Object.keys(dateCounts).sort();
            const data = sortedDates.map((date) => dateCounts[date]);

            myChart.setOption({
                title: { text: "Daily Published Publications", left: "center", textStyle: { fontSize: 14, fontFamily: "Cormorant Garamond" } },
                tooltip: { trigger: "axis" },
                grid: { top: 54, right: 22, bottom: 36, left: 42 },
                xAxis: { type: "category", data: sortedDates },
                yAxis: { type: "value", minInterval: 1 },
                series: [{ data, type: "line", smooth: true, itemStyle: { color: "#8f4b2f" } }]
            });
            myChart.resize();
        },
        renderCommentLineChart() {
            const chartDom = document.getElementById("chart-comments");
            if (!chartDom || typeof echarts === "undefined") {
                return;
            }
            const myChart = echarts.getInstanceByDom(chartDom) || echarts.init(chartDom);
            const dateCounts = {};
            this.publishedHomepagePosts.forEach((post) => {
                const date = String(post.createdAt || "").substring(0, 10);
                if (date) {
                    dateCounts[date] = (dateCounts[date] || 0) + Number(post.commentCount || 0);
                }
            });
            const sortedDates = Object.keys(dateCounts).sort();
            const data = sortedDates.map((date) => dateCounts[date]);

            myChart.setOption({
                title: { text: "Discussion Activity Trends", left: "center", textStyle: { fontSize: 14, fontFamily: "Cormorant Garamond" } },
                tooltip: { trigger: "axis" },
                grid: { top: 54, right: 22, bottom: 36, left: 42 },
                xAxis: { type: "category", data: sortedDates },
                yAxis: { type: "value", minInterval: 1 },
                series: [{ data, type: "line", smooth: true, itemStyle: { color: "#6d7561" } }]
            });
            myChart.resize();
        },
        renderCategoryPieChart() {
            const chartDom = document.getElementById("chart-categories");
            if (!chartDom || typeof echarts === "undefined") {
                return;
            }
            const myChart = echarts.getInstanceByDom(chartDom) || echarts.init(chartDom);
            myChart.off("click");
            myChart.off("legendselectchanged");

            const pieData = this.categoryPieLegendItems.map((item) => ({
                name: item.name,
                value: item.count
            }));

            myChart.setOption({
                color: categoryPieColors,
                title: { text: "Publication Categories", textStyle: { fontSize: 14, fontFamily: "Cormorant Garamond" }, left: "center" },
                tooltip: { trigger: "item" },
                legend: {
                    show: false,
                    selectedMode: false
                },
                series: [{
                    type: "pie",
                    selectedMode: false,
                    radius: ["38%", "72%"],
                    center: ["50%", "55%"],
                    label: { show: false },
                    labelLine: { show: false },
                    emphasis: { focus: "none", scale: true },
                    itemStyle: { borderRadius: 5, borderColor: "#fff", borderWidth: 2 },
                    data: pieData
                }]
            }, true);
            myChart.resize();
        }
    }
}).mount("#app");
