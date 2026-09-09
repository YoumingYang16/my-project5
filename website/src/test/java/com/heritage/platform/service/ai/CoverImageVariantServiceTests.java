package com.heritage.platform.service.ai;

import com.heritage.platform.entity.Category;
import com.heritage.platform.entity.Post;
import com.heritage.platform.entity.User;
import com.heritage.platform.enums.UserRole;
import com.heritage.platform.repository.PostRepository;
import com.heritage.platform.service.AuthContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoverImageVariantServiceTests {

    @Test
    void createsNonDestructiveCropFitAndStretchVariants(@TempDir Path tempDir) throws Exception {
        AuthContextService auth = mock(AuthContextService.class);
        UploadPathService paths = mock(UploadPathService.class);
        PostRepository posts = mock(PostRepository.class);
        User admin = mock(User.class);
        Post publication = Post.create("Paper", "Content", null, null, null, mock(User.class), mock(Category.class));
        Path sourcePath = tempDir.resolve("source.png");
        Path generated = Files.createDirectories(tempDir.resolve("generated"));

        BufferedImage source = new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();
        ImageIO.write(source, "png", sourcePath.toFile());

        when(auth.requireActiveUser()).thenReturn(admin);
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(posts.findById(7L)).thenReturn(Optional.of(publication));
        when(paths.resolveSelectedGeneratedCover(7L, "/uploads/source.png")).thenReturn(sourcePath);
        when(paths.generatedCoverDirectory(7L)).thenReturn(generated);
        when(paths.toUploadUrl(any(Path.class))).thenReturn("/uploads/generated-covers/7/derived/result.jpg");

        CoverImageVariantService service = new CoverImageVariantService(auth, paths, posts);
        service.derive(7L, "/uploads/source.png", 300, 300, "contain", 50d, 50d, null, null, null, null, "#00FF00");
        service.derive(7L, "/uploads/source.png", 320, 480, "stretch", 50d, 50d, null, null, null, null, "#FFFFFF");
        service.derive(7L, "/uploads/source.png", 900, 600, "cover", 75d, 50d, 25d, 0d, 50d, 100d, "#FFFFFF");

        var files = Files.list(generated.resolve("derived"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        assertThat(files).hasSize(3);

        BufferedImage fit = imageWithSize(files, 300, 300);
        Color corner = new Color(fit.getRGB(0, 0));
        assertThat(corner.getGreen()).isGreaterThan(250);
        assertThat(corner.getRed()).isLessThan(5);
        assertThat(corner.getBlue()).isLessThan(5);
        assertThat(imageWithSize(files, 320, 480)).isNotNull();
        assertThat(imageWithSize(files, 900, 600)).isNotNull();
        assertThat(ImageIO.read(sourcePath.toFile()).getWidth()).isEqualTo(400);
        assertThat(ImageIO.read(sourcePath.toFile()).getHeight()).isEqualTo(200);
    }

    private BufferedImage imageWithSize(java.util.List<Path> files, int width, int height) throws Exception {
        for (Path file : files) {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image.getWidth() == width && image.getHeight() == height) return image;
        }
        return null;
    }
}