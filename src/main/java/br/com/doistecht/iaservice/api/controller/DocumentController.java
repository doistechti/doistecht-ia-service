package br.com.doistecht.iaservice.api.controller;

import br.com.doistecht.iaservice.api.dto.DocumentResponse;
import br.com.doistecht.iaservice.client.AuthenticatedClient;
import br.com.doistecht.iaservice.document.DocumentService;
import br.com.doistecht.iaservice.document.InvalidDocumentException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Documentos", description = "Documentos usados como fonte nas perguntas (RAG)")
@RestController
@RequestMapping("/v1/documents")
public class DocumentController {

	private final DocumentService documentService;

	public DocumentController(DocumentService documentService) {
		this.documentService = documentService;
	}

	@Operation(summary = "Envia um documento (PDF, TXT ou MD) para ser indexado",
			description = "O processamento é assíncrono: o documento volta com status PROCESSING. "
					+ "Consulte GET /v1/documents/{id} até o status ficar READY.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file,
			@Parameter(hidden = true) @RequestAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) AuthenticatedClient client) {
		var document = documentService.upload(client, file.getOriginalFilename(), read(file));
		var location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(document.getId())
				.toUri();
		// 202: o documento foi aceito, mas ainda não está pronto para consulta
		return ResponseEntity.accepted().location(location).body(DocumentResponse.from(document));
	}

	@Operation(summary = "Lista os documentos do cliente")
	@GetMapping
	public List<DocumentResponse> list(
			@Parameter(hidden = true) @RequestAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) AuthenticatedClient client) {
		return documentService.list(client.id()).stream().map(DocumentResponse::from).toList();
	}

	@Operation(summary = "Consulta um documento e o status do processamento")
	@GetMapping("/{id}")
	public DocumentResponse get(@PathVariable Long id,
			@Parameter(hidden = true) @RequestAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) AuthenticatedClient client) {
		return DocumentResponse.from(documentService.get(client.id(), id));
	}

	@Operation(summary = "Apaga o documento e seus trechos indexados")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id,
			@Parameter(hidden = true) @RequestAttribute(AuthenticatedClient.REQUEST_ATTRIBUTE) AuthenticatedClient client) {
		documentService.delete(client.id(), id);
		return ResponseEntity.noContent().build();
	}

	private static byte[] read(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new InvalidDocumentException("Não foi possível ler o arquivo enviado.", ex);
		}
	}

}
