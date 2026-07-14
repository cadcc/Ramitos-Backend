$version: "2"

namespace mufasa

use smithy4s.meta#vector
use smithy4s.meta#refinement
use alloy#simpleRestJson
use alloy#dateFormat

@vector
list StringList {
    member: String
}

@vector
list IntList {
    member: Integer
}

intEnum IntBool {
    TRUE = 1
    FALSE = 0
}

intEnum IntFlag {
    SET = 1
}

map StringMap {
    key: String
    value: String
}

@dateFormat
string Date

@simpleRestJson
@httpBearerAuth
service MufasaApi {
    version: "1.0.0"
    operations: [ListCarreras,ListPlanes,ListRamos,GetMalla,ListPeriodos,ListSemanas,ListCursosInscritos,ListCarrerasAlumnos,ListCursos]
}

@http(method: "GET", uri: "/carreras")
@readonly
operation ListCarreras {
    input := {
        @httpQuery("vigente")
        vigente: IntBool

        @httpQuery("terminal")
        terminal: IntBool

        @httpQuery("tipo_titulo")
        tipoTitulo: TipoTitulo
        
        @httpQuery("id_carrera[]")
        idsCarrera: StringList

        @httpQuery("desde")
        desde: Date
    }
    output := {
        @httpPayload
        @required
        content: Carreras
    }
}

@http(method: "GET", uri: "/planes")
@readonly
operation ListPlanes {
    input := {
        @httpQuery("id_carrera[]")
        idsCarrera: IntList

        @httpQuery("id_plan[]")
        idsPlan: IntList
    }
    output := {
        @httpPayload
        @required
        content: Planes
    }
}

@http(method: "GET", uri: "/ramos")
@readonly
operation ListRamos {
    input := {
        @httpQuery("id_ramo[]")
        idsRamo: StringList

        @httpQuery("q")
        query: String

        @httpQuery("desde") 
        desde: Date
    }
    output := {
        @httpPayload
        @required
        content: Ramos
    }
}

@http(method: "GET", uri: "/malla")
@readonly
operation GetMalla {
    input := {
        @httpQuery("id_plan")
        @required
        idPlan: String

        @httpQuery("obligatorio")
        obligatorio: IntBool
    }
    output := {
        @httpPayload
        @required
        content: Malla
    }
}

@http(method: "GET", uri: "/periodos")
@readonly
operation ListPeriodos {
    input := {
        @httpQuery("activo")
        activo: IntFlag
    }
    output := {
        @httpPayload
        @required
        content: Periodos 
    }
}

@http(method: "GET", uri: "/semanas")
@readonly
operation ListSemanas {
    input := {
        @httpQuery("id_periodo[]")
        idsPeriodo: StringList
    }
    output := {
        @httpPayload
        @required
        content: Semanas
    }
}

@http(method: "GET", uri: "/cursos")
@readonly
operation ListCursos {
    input := {
        @httpQuery("id_periodo[]")
        idsPeriodo: StringList

        @httpQuery("id_ramo[]")
        idsRamo: StringList

        @httpQuery("id_curso[]")
        idsCurso: StringList

        @httpQuery("desde")
        desde: Date
    }
    output := {
        @httpPayload
        @required
        content: Cursos
    }
}

@http(method: "GET", uri: "/cursos_inscritos")
@readonly
operation ListCursosInscritos {
    input := {
        @httpQuery("rut[]")
        ruts: StringList

        @httpQuery("periodo[]")
        idsPeriodo: StringList

        @httpQuery("id_curso[]")
        idsCurso: StringList

        @httpQuery("desde")
        desde: Date
    }
    output := {
        @httpPayload
        @required
        content: CursosInscritos
    }
}

@http(method: "GET", uri: "/carreras_alumnos")
@readonly
operation ListCarrerasAlumnos {
    input := {
        @httpQuery("rut[]")
        ruts: StringList

        @httpQuery("desde")
        desde: Date
    }
    output := {
        @httpPayload
        @required
        content: CarrerasAlumnos
    }
}

@vector
list Carreras {
    member: Carrera
}

@vector
list Planes {
    member: Plan
}

@vector
list Malla {
    member: CursoMalla
}

@vector
list Periodos {
    member: Periodo
}

@vector
list Semanas {
    member: Semana
}

@vector
list CursosInscritos {
    member: CursoInscrito
}

@vector
list CarrerasAlumnos {
    member: CarreraAlumno
}

@vector
list Ramos {
    member: Ramo
}

@vector
list Cursos {
    member: Curso
}

structure Carrera {
    @jsonName("terminal")
    terminal: IntBool

    @jsonName("tipo_titulo")
    tipoTitulo: TipoTitulo
    
    @jsonName("id_institucion")
    idInstitution: Integer
    
    @jsonName("nombre")
    nombre: String
    
    @jsonName("titulo_femenino")
    tituloFemenino: String
    
    @jsonName("titulo_masculino")
    tituloMasculino: String
    
    @jsonName("id_carrera")
    idCarrera: Integer
    
    @jsonName("id_licenciatura_asociada")
    idLicenciaturaAsociada: Integer
    
    @jsonName("extras")
    extras: StringMap
    
    @jsonName("id_estado")
    vigente: IntBool
    
    @jsonName("codigo_carrera")
    codigo: String
    
    @jsonName("institucion")
    nombreInstitucion: String
    
    @jsonName("tipo_titulo_texto")
    tipoTituloTexto: String
    
    @jsonName("estado")
    estado: String
}

intEnum TipoTitulo {
    INDEFINIDO = 0
    LICENCIATURA = 1
    TITULO_PROFESIONAL = 2
    MAGISTER = 3
    DOCTORADO = 4
    ESPECIALIZACION = 7
}

structure Plan {
    /// The format of this date field is unknown.
    @jsonName("fecha_termino")
    fechaTermino: String
    
    @jsonName("descripcion")
    descripcion: String
    
    @jsonName("nombre")
    nombre: String
    
    @jsonName("version")
    version: Integer
    
    @jsonName("id_carrera")
    idCarrera: String
    
    @jsonName("fecha_creacion")
    fechaCreacion: String
    
    @jsonName("codigo_carrera")
    codigoCarrera: String
    
    @jsonName("id_plan")
    idPlan: String
}

structure Ramo {
    @jsonName("id_ramo")
    idRamo: String
    
    @jsonName("id_externo")
    idExterno: String
    
    @jsonName("codigo")
    codigo: String
    
    @jsonName("id_institucion")
    idInstitucion: String
    
    @jsonName("nombre")
    nombre: String
    
    @jsonName("equivalencia")
    equivalencia: String
    
    @jsonName("sct")
    sct: String
    
    @jsonName("comentario")
    comentario: String
    
    @jsonName("escala")
    escala: String
    
    @jsonName("ud")
    ud: String
    
    @jsonName("requisito")
    requisito: String
    
    @jsonName("id_escala")
    idEscala: Integer
    
    @jsonName("name")
    nombreIngles: String
}

structure CursoMalla {
    @jsonName("codigo")
    codigo: String
    
    @jsonName("nombre")
    nombre: String
    
    @jsonName("plan_nombre")
    nombrePlan: String
    
    @jsonName("version")
    version: String
    
    @jsonName("cantidad")
    cantidad: Integer
    
    @jsonName("id_carrera")
    idCarrera: String
    
    @jsonName("nivel")
    nivel: String
    
    @jsonName("regla")
    regla: String
    
    @jsonName("sct")
    sct: String
    
    @jsonName("ud")
    ud: String
    
    @jsonName("id_ramo")
    idRamo: Integer
    
    @jsonName("id_plan")
    idPlan: String
}

structure Periodo {
    @jsonName("id_periodo")
    idPeriodo: String
    
    @jsonName("fecha_inicio")
    fechaInicio: String
    
    @jsonName("activo")
    activo: Integer
}

structure Semana {
    @jsonName("semana")
    semana: Integer
    
    @jsonName("id_periodo")
    idPeriodo: String
    
    @jsonName("fecha_inicio")
    fechaInicio: String
}

structure CursoInscrito {
    @jsonName("rut")
    rut: String
    
    @jsonName("id_periodo")
    idPeriodo: String
    
    @jsonName("id_curso")
    idCurso: Integer
    
    @jsonName("id_estado_curso")
    idEstadoCurso: String
    
    @jsonName("id_ramo")
    idRamo: Integer
}

structure CarreraAlumno {
    @jsonName("rut")
    rut: String
    
    @jsonName("estado_texto")
    estadoTexto: String
    
    @jsonName("id_carrera")
    idCarrera: Integer
}

structure Curso {
    @jsonName("cupo")
    cupo: Integer
    
    @jsonName("departamento")
    departamento: String
    
    @jsonName("id_curso")
    idCurso: String
    
    @jsonName("sct")
    sct: String
    
    @jsonName("name")
    nombreIngles: String
    
    @jsonName("codigo")
    codigo: String
    
    @jsonName("extras")
    extras: StringMap
    
    @jsonName("comentario")
    comentario: String
    
    @jsonName("id_ramo")
    idRamo: String
    
    @jsonName("id_externo")
    idExterno: String
    
    @jsonName("seccion")
    seccion: String
    
    @jsonName("periodo_texto")
    periodoTexto: String
    
    @jsonName("requisito")
    requisito: String
    
    @jsonName("modalidad")
    modalidad: String
    
    @jsonName("id_institucion")
    idInstitucion: String
    
    @jsonName("nombre")
    nombre: String
    
    @jsonName("escala_notas")
    escalaNotas: String
    
    @jsonName("ud")
    ud: String
    
    @jsonName("escala")
    escala: String
    
    @jsonName("ano")
    anno: String
    
    @jsonName("periodo")
    periodo: String
    
    @jsonName("tema")
    tema: String
}

