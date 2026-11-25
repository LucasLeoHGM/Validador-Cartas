package br.com.validacarta.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificacaoRepository extends JpaRepository<VerificacaoRegistro, Long> {

    List<VerificacaoRegistro> findTop50ByOrderByIdDesc();
}
