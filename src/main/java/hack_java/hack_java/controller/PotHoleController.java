package hack_java.hack_java.controller;

import hack_java.hack_java.dto.PotHoleDTO;
import hack_java.hack_java.service.PotHoleService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("api/potHole")
@AllArgsConstructor
public class PotHoleController {

    public final PotHoleService potHoleService;

    @PostMapping
    public ResponseEntity save(@RequestPart("file") MultipartFile file,
            @RequestBody PotHoleDTO dto) {
        potHoleService.save(file, dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<PotHoleDTO>> getAll(@RequestParam int pageNo,
                                                   @RequestParam int size) {
        return ResponseEntity.ok(potHoleService.getAll(pageNo, size));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity delete(@PathVariable Long id) {
        potHoleService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<PotHoleDTO> update(@PathVariable Long id, @RequestBody PotHoleDTO dto) {
        return ResponseEntity.ok(potHoleService.update(id, dto));
    }
}