CREATE TABLE immport.lk_cell_population_marker
(
    name VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    link VARCHAR(2000),
    PRIMARY KEY (name)
);


CREATE TABLE immport.lk_hmdb
(
    hmdb_id VARCHAR(15) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    link VARCHAR(2000),
    PRIMARY KEY (hmdb_id)
);


CREATE TABLE immport.lk_mass_spectrometry_type
(
    name VARCHAR(50) NOT NULL,
    description VARCHAR(4000),
    link VARCHAR(2000),
    PRIMARY KEY (name)
);


CREATE TABLE immport.lk_protein_name
(
    name VARCHAR(255) NOT NULL,
    uniprot_id VARCHAR(50) NOT NULL,
    uniprot_gene_name VARCHAR(255),
    description VARCHAR(4000),
    link VARCHAR(2000),
    PRIMARY KEY (name)
);


CREATE TABLE immport.mass_spectrometry_result
(
    result_id INT NOT NULL,
    arm_accession VARCHAR(15),
    biosample_accession VARCHAR(15),
    comments VARCHAR(500),
    experiment_accession VARCHAR(15) NOT NULL,
    expsample_accession VARCHAR(15) NOT NULL,
    file_info_id INT,
    intensity FLOAT NOT NULL,
    retention_time FLOAT,
    retention_time_unit VARCHAR(25),
    m_z_ratio FLOAT,
    z_charge VARCHAR(50),
    database_id_reported VARCHAR(50),
    database_id_preferred VARCHAR(25),
    mass_spectrometry_type VARCHAR(50) NOT NULL,
    metabolite_name_reported VARCHAR(255),
    metabolite_name_preferred VARCHAR(255),
    protein_name_reported VARCHAR(255),
    protein_name_preferred VARCHAR(255),
    repository_accession VARCHAR(20),
    repository_name VARCHAR(50),
    study_accession VARCHAR(15),
    study_time_collected                 FLOAT,
    study_time_collected_unit            VARCHAR(25),
    subject_accession VARCHAR(15),
    workspace_id INT,
    PRIMARY KEY (result_id)
);


CREATE TABLE immport.study_data_release
(
    study_accession VARCHAR(15) NOT NULL,
    data_release_version INT,
    data_release_date DATE,
    status VARCHAR(50),
    PRIMARY KEY (study_accession, data_release_version, data_release_date)
);