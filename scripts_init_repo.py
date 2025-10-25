import os
import shutil
from github import Github

# --- 設定 ---
TEMPLATE_DIR = os.path.join(os.path.dirname(__file__), "templates")
GITIGNORE_PATH = os.path.join(TEMPLATE_DIR, "gitignore_template.txt")
README_PATH = os.path.join(TEMPLATE_DIR, "readme_template.md")
OUTPUT_GITIGNORE = os.path.join(os.getcwd(), ".gitignore")
OUTPUT_README = os.path.join(os.getcwd(), "README.md")

def create_gitignore():
    shutil.copy(GITIGNORE_PATH, OUTPUT_GITIGNORE)
    print("✅ .gitignore generated.")

def create_readme(repo_name):
    with open(README_PATH, "r", encoding="utf-8") as f:
        template = f.read()
    content = template.replace("{{REPO_NAME}}", repo_name)
    with open(OUTPUT_README, "w", encoding="utf-8") as f:
        f.write(content)
    print("✅ README.md generated.")

def create_github_repo(token, repo_name, private=False):
    g = Github(token)
    user = g.get_user()
    repo = user.create_repo(repo_name, private=private)
    print(f"✅ Repository created: {repo.clone_url}")

if __name__ == "__main__":
    token = os.getenv("GITHUB_TOKEN")
    if not token:
        print("❌ Error: 環境変数 GITHUB_TOKEN が設定されていません。")
        print("   PowerShellで次を実行してください：")
        print('   setx GITHUB_TOKEN "your_token_here"')
        exit(1)

    repo_name = input("新しいリポジトリ名を入力してください: ").strip()
    private_choice = input("プライベートリポジトリにしますか？(y/n): ").strip().lower()
    private = private_choice == "y"

    create_gitignore()
    create_readme(repo_name)
    create_github_repo(token, repo_name, private)

    print("🎉 完了！新しいGitHubリポジトリが作成されました。")
