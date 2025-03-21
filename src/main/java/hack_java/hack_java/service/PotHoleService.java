package hack_java.hack_java.service;

import hack_java.hack_java.dto.PotHoleDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PotHoleService {

    void save(MultipartFile file, PotHoleDTO dto);

    void delete(Long id);

    PotHoleDTO update(Long id, PotHoleDTO dto);

    List<PotHoleDTO> getAll(int pageNo, int size);

}