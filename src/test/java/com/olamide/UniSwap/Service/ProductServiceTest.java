package com.olamide.UniSwap.Service;

import com.olamide.UniSwap.Dto.ProductDTO;
import com.olamide.UniSwap.Entity.Product;
import com.olamide.UniSwap.Entity.ProductStatus;
import com.olamide.UniSwap.Entity.User;
import com.olamide.UniSwap.Repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Pure unit tests. ProductRepository, UserService, and FileStorageService are
// mocked — these verify ProductService's own business rules (defaults,
// ownership enforcement) without touching a real database.
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserService userService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ProductService productService;

    private User seller;
    private User otherUser;
    private Product product;
    private ProductDTO productDTO;

    @BeforeEach
    void setUp() {
        seller = User.builder().id(1L).username("olamide").build();
        otherUser = User.builder().id(2L).username("someone-else").build();

        product = Product.builder()
                .id(10L)
                .title("Used HP Laptop")
                .price(new BigDecimal("85000.00"))
                .category("Electronics")
                .itemCondition("Neatly Used")
                .status(ProductStatus.AVAILABLE)
                .imageUrl("http://localhost:8080/uploads/some-file.jpg")
                .seller(seller)
                .build();

        productDTO = ProductDTO.builder()
                .title("Used HP Laptop")
                .price(new BigDecimal("85000.00"))
                .category("Electronics")
                .itemCondition("Neatly Used")
                // Deliberately trying to sneak a SOLD status through on create —
                // this must be ignored by the service, see the test below.
                .status("SOLD")
                .build();
    }

    @Test
    void create_ignoresClientSuppliedStatus_alwaysCreatesAsAvailable() {
        when(userService.getById(1L)).thenReturn(seller);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = productService.create(productDTO, 1L);

        // The DTO said "SOLD", but the entity's own @PrePersist default
        // (AVAILABLE) is what should win — the service never copies
        // dto.getStatus() onto the entity at all.
        assertThat(created.getStatus()).isNotEqualTo(ProductStatus.SOLD);
        assertThat(created.getSeller()).isEqualTo(seller);
    }

    @Test
    void create_rejectsUnknownCategory() {
        productDTO.setCategory("Not-A-Real-Category");

        assertThatThrownBy(() -> productService.create(productDTO, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verify(productRepository, never()).save(any());
    }

    @Test
    void create_rejectsUnknownCondition() {
        productDTO.setItemCondition("Mint");

        assertThatThrownBy(() -> productService.create(productDTO, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verify(productRepository, never()).save(any());
    }

    @Test
    void getById_throwsNotFound_whenProductDoesNotExist() {
        when(productRepository.findByIdWithSeller(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void update_succeeds_whenRequesterIsTheOwner() {
        when(productRepository.findByIdWithSeller(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductDTO updateDto = ProductDTO.builder()
                .title("Used HP Laptop (Price Reduced)")
                .price(new BigDecimal("75000.00"))
                .category("Electronics")
                .itemCondition("Neatly Used")
                .build();

        Product updated = productService.update(10L, updateDto, 1L); // seller's own id

        assertThat(updated.getTitle()).isEqualTo("Used HP Laptop (Price Reduced)");
        assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("75000.00"));
    }

    @Test
    void update_throwsForbidden_whenRequesterIsNotTheOwner() {
        when(productRepository.findByIdWithSeller(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.update(10L, productDTO, 2L)) // NOT the seller
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verify(productRepository, never()).save(any());
    }

    @Test
    void markAsSold_throwsForbidden_whenRequesterIsNotTheOwner() {
        when(productRepository.findByIdWithSeller(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.markAsSold(10L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verify(productRepository, never()).save(any());
        // The status must remain untouched after a rejected attempt.
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
    }

    @Test
    void markAsSold_succeeds_whenRequesterIsTheOwner() {
        when(productRepository.findByIdWithSeller(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product sold = productService.markAsSold(10L, 1L);

        assertThat(sold.getStatus()).isEqualTo(ProductStatus.SOLD);
    }

    @Test
    void uploadImage_throwsForbidden_whenRequesterIsNotTheOwner_beforeStoringAnyFile() {
        when(productRepository.findByIdWithSeller(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.uploadImage(10L, null, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        // The crucial security property: no file reaches disk when the
        // requester doesn't own the listing.
        verify(fileStorageService, never()).store(any());
        verify(fileStorageService, never()).delete(anyString());
    }

    @Test
    void delete_throwsForbidden_whenRequesterIsNotTheOwner() {
        when(productRepository.findByIdWithSeller(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.delete(10L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verify(productRepository, never()).delete(any());
    }

    @Test
    void delete_succeeds_whenRequesterIsTheOwner_andCleansUpTheImage() {
        when(productRepository.findByIdWithSeller(10L)).thenReturn(Optional.of(product));

        productService.delete(10L, 1L);

        verify(productRepository).delete(product);
        // Deleting a listing must also remove its image from disk.
        verify(fileStorageService).delete("http://localhost:8080/uploads/some-file.jpg");
    }
}
