import argparse

def main():
    parser = argparse.ArgumentParser(description="Fullstack Scaffolder")
    parser.add_argument("project_path", help="Ruta del proyecto")
    args = parser.parse_args()
    print(f"Iniciando andamiaje fullstack en: {args.project_path}")

if __name__ == "__main__":
    main()
