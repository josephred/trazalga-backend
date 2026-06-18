import argparse

def main():
    parser = argparse.ArgumentParser(description="Project Scaffolder")
    parser.add_argument("target_path", help="Ruta objetivo")
    parser.add_argument("--verbose", action="store_true", help="Salida detallada")
    args = parser.parse_args()
    print(f"Analizando proyecto en: {args.target_path}")

if __name__ == "__main__":
    main()
