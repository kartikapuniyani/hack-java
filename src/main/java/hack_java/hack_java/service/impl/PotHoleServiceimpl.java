package hack_java.hack_java.service.impl;

import hack_java.hack_java.dto.PotHoleDTO;
import hack_java.hack_java.repository.PotHoleRepository;
import hack_java.hack_java.service.PotHoleService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class PotHoleServiceimpl implements PotHoleService {

    public PotHoleRepository repository;

    @Override
    public void save(MultipartFile file, PotHoleDTO dto) {
    }

    @Override
    public void delete(Long id) {

    }

    @Override
    public PotHoleDTO update(Long id, PotHoleDTO dto) {
        return null;
    }

    @Override
    public List<PotHoleDTO> getAll(int pageNo, int size) {
        return null;
    }
}