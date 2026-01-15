package com.trazalga.api.services;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trazalga.api.models.*;
import com.trazalga.api.repositories.IDeclaracionRecolectorRepository;
import com.trazalga.api.dto.PerformanceReport;

@Service
public class StressTestService {

    @Autowired
    private IDeclaracionRecolectorRepository recolectorRepo;

    private Long generarIndice() {
        return Long.parseLong(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
    }
    // ... otros repositorios

    // Eliminamos @Transactional para que los registros se guarden por lotes y no al
    // final de todo
    public PerformanceReport generarCargaMasiva(int cantidad) {
        long startTime = System.currentTimeMillis();
        Random random = new Random();

        System.out.println("Iniciando carga masiva de " + cantidad + " registros...");

        for (int i = 1; i <= cantidad; i++) {
            // Generamos un ID único combinando el timestamp con el contador para evitar
            // colisiones
            long baseIndice = Long.parseLong(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmm")));
            Long indice = Long.parseLong(baseIndice + "" + i);
            DeclaracionRecolectorModel data = new DeclaracionRecolectorModel()
                    .setId(indice)
                    .setFolioOrigen("" + indice)
                    .setFolioDesembarqueRo("RO" + indice)
                    .setDesembarque(new BigDecimal(100 + (1000 - 100) * random.nextDouble()))
                    .setCaptura(new BigDecimal(100 + (1000 - 100) * random.nextDouble()))
                    .setFechaDeclaracion(new Date())
                    .setFechaExtraccion(new Date())
                    .setHora("12:00:00")
                    .setNombre("Recolector " + indice)
                    .setCodigoSernapesca("RPA-" + indice)
                    .setVaradero("Varadero Central")
                    .setLatitud(-33.4489)
                    .setLongitud(-70.6693)
                    .setHumedad("15%")
                    .setCodigoDestinatario("DEST-" + indice)
                    .setNombreDestinatario("Destinatario " + indice)
                    .setDeclaracionDestinatario(indice + 1000)
                    .setCaleta(new CaletaModel().setId(1L))
                    .setUsuario(new UsuarioModel().setId(1L))
                    .setUsuarioDestinatario(new UsuarioModel().setId(2L))
                    .setEspecie(new EspecieModel().setId(1L))
                    .setComuna(new ComunaModel().setId(1L))
                    .setExtraccionTipo(new ExtraccionTipoModel().setId(1L))
                    .setComposicion(new ComposicionModel().setId(1L))
                    .setHumedadEstado(new HumedadEstadoModel().setId(1L));

            recolectorRepo.save(data);

            // Logging de progreso y persistencia cada 500 registros
            if (i % 500 == 0) {
                recolectorRepo.flush();
                System.out.println("Progreso: " + i + " de " + cantidad + " registros guardados...");
            }
        }

        long endTime = System.currentTimeMillis();
        return new PerformanceReport(cantidad, startTime, endTime);
    }

    public void runStressTest() {
        // Placeholder for stress test logic
    }
}