import { Icon } from "../../../components/ui/Icon";

export interface RoleHeroProps {
  level: string;
  code: string;
  name: string;
  focus: string;
}

export function RoleHero({ level, code, name, focus }: RoleHeroProps) {
  return (
    <section className="hero" aria-labelledby="role-hero-title">
      <div>
        <p className="kicker">
          {level.toUpperCase()} · {code}
        </p>
        <h2 className="hero__title" id="role-hero-title">
          Good morning.
          <br />
          {name}.
        </h2>
        <p>{focus}</p>
        <a className="hero__cta" href="#role-queue">
          View work queue
          <Icon name="send" size={16} />
        </a>
      </div>
      <div className="hero__art" aria-hidden="true">
        <span className="halo" />
        <span className="leaf" />
        <span className="star" />
      </div>
    </section>
  );
}
